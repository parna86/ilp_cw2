package uk.ac.ed.inf.aqmaps;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson;

import java.lang.reflect.Type;

import com.google.gson.reflect.TypeToken;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;
import com.mapbox.turf.TurfMeasurement;

/**
 * Things to keep in hand
 * Run WebServer: java -jar WebServerLite.jar
 * Run aqmaps: java -jar aqmaps-0.0.1-SNAPSHOT.jar 15 06 2021 55.9444 -3.1878 5678 80
 * Turf documentation : https://docs.mapbox.com/android/java/overview/turf/#working-with-geojson
 * 						https://docs.mapbox.com/archive/android/java/api/libjava-turf/4.0.0/com/mapbox/turf/TurfJoins.html
 * To calculate direction of one coordinate: https://docs.oracle.com/javase/7/docs/api/java/lang/Math.html#atan2%28double,%20double%29
 * https://math.stackexchange.com/questions/796243/how-to-determine-the-direction-of-one-point-from-another-given-their-coordinate
 * "From (𝑥1,𝑦1) to (𝑥2,𝑦2) the direction is atan2(𝑦2−𝑦1,𝑥2−𝑥1) - You wrote you were writing a program, so atan2 
 * is the easiest solution, it's usually found in your languages math package. In java it's Math.atan2(y, x)"
 * https://stackoverflow.com/questions/1311049/how-to-map-atan2-to-degrees-0-360
 */						
class Coordinate{
	double lng;
	double lat;
}

class ww3ToCoord{
//	String country;
//	String nearestPlace;
	String words;
//	String language;
//	String map;
	Coordinate coordinates;
	
}

class SensorReadings{
	String location;
	double battery;
	String reading;
	Coordinate coordinates;
	boolean isRead = false;
}


public class App
{
	
    public static void main( String[] args ) throws IOException, InterruptedException
    {
    	/**
    	 * args[2] = year
    	 * args[1] = month
    	 * args[0] = date
    	 * args[3] = starting latitude
    	 * args[4] = starting longitude
    	 * args[6] = port
    	 */
    	
    	/*
    	 * Below are the borders for the map
    	 */
    	final var long_W = (-3.192473);
    	final var long_E = (-3.184319);
    	final var lat_N = (55.946233);
    	final var lat_S = (55.942617);
    	Polygon map = Polygon.fromLngLats(constructMap(lat_N, lat_S, long_E, long_W));
    	
    	
    	String AQuri = "http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json";
    	String BDuri = "http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson";
        
    	String currReadings = sendHTTP(AQuri);
    	String noFlyZones = sendHTTP(BDuri);
    	
    	//starting point
        var start = Point.fromLngLat(Double.parseDouble(args[4]), Double.parseDouble(args[3]));
        
        
        //this is for finding the actual coordinates for each of the ww3 strings - storing data in SensorReadings object only
        Type listType = new TypeToken<ArrayList<SensorReadings>>() {}.getType();
        ArrayList<SensorReadings> readings = new Gson().fromJson(currReadings, listType);
        for(SensorReadings one : readings) {
        	one.coordinates = convertCoord(one.location); //these are all the readings from one day 
        }
        
        //below - extracting each of the coordinates for the buildings in a list of list of points.
        
        FeatureCollection bldgs = FeatureCollection.fromJson(noFlyZones.toString());
        
        ArrayList<Feature> f = new ArrayList<Feature>(bldgs.features());
        Map<String, Polygon> buildings = new HashMap<String, Polygon>();
        
        var features = new ArrayList<Feature>();
        
        /*
         * buildings is a hashmap that stores the polygon that represents each obstacle.  
         */
        for(Feature feature : f) {
        	String name = feature.properties().get("name").toString();
        	buildings.put(name.substring(1, name.length()-1), (Polygon)feature.geometry());
        }
        
        //for the geojson file with output path
        ArrayList<Feature> markers = new ArrayList<Feature>();
        ArrayList<Point> line_points = new ArrayList<Point>();
        line_points.add(start);
        		
        /*MAIN ALGO */
        double[] currPoint = {start.latitude(), start.longitude()};
        String move = "";
        var moveCount = 0;
        
        while(moveCount <= 150) {
        	System.out.println("Move " + moveCount);
        	System.out.println("Curr Point: " + currPoint[1] + "," + currPoint[0]);
        	double minDist = 10;
        	int closestIndex = -1;
        	for(SensorReadings one: readings) {
        		double[] sensorPoint = {one.coordinates.lat, one.coordinates.lng};
        		if(one.isRead) {
        			continue;
        		}
        		var currDist = euclidDist(currPoint, sensorPoint);
//        		System.out.println("_________curr dist___________");
//        		System.out.println(currDist);
        		if(currDist <= 0.0002) {
        			one.isRead = true;
        			break;
        		}
        		if(minDist > currDist) {
        			minDist = currDist;
        			closestIndex = readings.indexOf(one);
//        			System.out.println("Point: "+readings.get(closestIndex).coordinates.lat + "," + readings.get(closestIndex).coordinates.lng);
//        			System.out.println("Closest point updated to idx " + closestIndex);
        		}
        	}
        	if(closestIndex == -1) {
        		break;
        	}
        	System.out.println("______________________________________________________\n");
        	double[] closestPoint = {readings.get(closestIndex).coordinates.lat, readings.get(closestIndex).coordinates.lng};
//        	System.out.println("Index in sensor readings:" + closestIndex);
//        	System.out.println("Closest sensor point to curr point:" + closestPoint[0] + "," + closestPoint[1]);
        	//would've found potentially closest sensor
        	
        	/*figure out why the degree version of the direction is not being mapped for now it works*/
        	var direction = Math.atan2(closestPoint[1] - currPoint[1], closestPoint[0]-currPoint[0]);
//        	System.out.println("Direction of movement =====================================================");
//        	System.out.println(direction);
        	if(direction < 0) {
        		direction = direction + 2*Math.PI;
        	}
        	move = move + (moveCount+1) + "," + currPoint[0] + "," + currPoint[1] + "," + "<needtogetdirection>,";
//        	System.out.println(direction);
//        	var direction_deg = Math.toDegrees(direction);
//        	System.out.println("Tester ones below");
//        	System.out.println(Math.toDegrees(2*Math.PI));
//        	System.out.println(Math.toDegrees(Math.PI));
//        	System.out.println(direction_deg);
        	currPoint[0] = currPoint[0] + Math.cos(direction)*(0.0003);
        	currPoint[1] = currPoint[1] + Math.sin(direction)*(0.0003);
        	line_points.add(Point.fromLngLat(currPoint[1], currPoint[0]));
//        	System.out.println("New point:" + currPoint[1] + "," + currPoint[0]);
//        	System.out.println("______________________________________________________\n");
        	move = move + currPoint[0] + "," + currPoint[1] + ",";
        	if(euclidDist(currPoint, closestPoint) < 0.0002) {
        		readings.get(closestIndex).isRead = true;
        		move = move + readings.get(closestIndex).location + "\n";
        		Point sensorMarker = Point.fromLngLat(currPoint[1], currPoint[0]);
        		Feature sensorFeature = Feature.fromGeometry(sensorMarker);
        		String[] markerProps = findMarkerProperties(readings.get(closestIndex).battery, readings.get(closestIndex).reading);
        		sensorFeature.addStringProperty("marker-color", markerProps[0]);
        		sensorFeature.addStringProperty("marker-symbol", markerProps[1]);
        		markers.add(sensorFeature);
        	}
        	else {
        		move = move + "null\n";
        	}	
        	moveCount= moveCount+1;
        }//end of while loop
        markers.add(Feature.fromGeometry(LineString.fromLngLats(line_points)));
        FeatureCollection finalPath = FeatureCollection.fromFeatures(markers);
        writeGeoJSON(finalPath);
        System.out.println(move);
    }
    
    private static String[] findMarkerProperties(double battery, String reading) {
    	String[] props = new String[2];
    	double reading_n = 999;
    	if(reading.equals("null")) {
    		return props;
    	}
    	else {
    		reading_n = Double.parseDouble(reading);
    	}
    	// 0 - colour, 1 - marker
    	if(battery < 10.0) {
    		props[0] = "#000";
    		props[1] = "cross";
    	}
    	else if(reading_n >= 0 && reading_n < 32) {
    		props[0] = "#00ff00";
    		props[1] = "lighthouse";
    	}
    	else if(reading_n >= 32 && reading_n < 64) {
    		props[0] = "#40ff00";
    		props[1] = "lighthouse";
    	}
    	else if(reading_n >= 64 && reading_n < 96) {
    		props[0] = "#80ff00";
    		props[1] = "lighthouse";
    	}
    	else if(reading_n >= 96 && reading_n < 128) {
    		props[0] = "#c0ff00";
    		props[1] = "lighthouse";
    	}
    	else if(reading_n >= 128 && reading_n < 160) {
    		props[0] = "#ffc000";
    		props[1] = "danger";
    	}
    	else if(reading_n >= 160 && reading_n < 192) {
    		props[0] = "ff8000";
    		props[1] = "danger";
    	}
    	else if(reading_n >= 192 && reading_n < 224) {
    		props[0] = "ff4000";
    		props[1] = "danger";
    	}
    	else if(reading_n >= 224 && reading_n < 256) {
    		props[0] = "#ff0000";
    		props[1] = "danger";
    	}
    	return props;
    }
    
    
    private static int fitDirection(double direction) {
    	if(direction > 5 && direction <= 15) {
    		return 10;
    	}
    	else if(direction > 15 && direction <= 25) {
    		return 20;
    	}
    	else if(direction > 25 && direction <= 35) {
    		return 30;
    	}
    	else if(direction > 35 && direction <= 45) {
    		return 40;
    	}
    	else if(direction > 45 && direction <= 55) {
    		return 50;
    	}
    	else if(direction > 55 && direction <= 65) {
    		return 60;
    	}
    	else if(direction > 65 && direction <= 75) {
    		return 70;
    	}
    	else if(direction > 75 && direction <= 85) {
    		return 80;
    	}
    	else if(direction > 85 && direction <= 95) {
    		return 90;
    	}
    	else if(direction > 95 && direction <= 105) {
    		return 100;
    	}
    	else {
    		return 120;
    	}
    }
    /***
     * 
     * @param north
     * @param south
     * @param east
     * @param west
     * @return temp2 - a list of list of points - for polygons
     */
    
    private static List<List<Point>> constructMap(double north, double south, double east, double west){
    	
    	/**
    	 * ArrayList of coordinates below. Need to create that for individual polygons too.
    	 */
    	final var coordinates = new ArrayList<Point>();
    	coordinates.add(Point.fromLngLat(west, north));
    	coordinates.add(Point.fromLngLat(east, north));
    	coordinates.add(Point.fromLngLat(east, south));
    	coordinates.add(Point.fromLngLat(west, south));
    	coordinates.add(Point.fromLngLat(west, north));
    	
    	List<List<Point>> temp2 = new ArrayList<List<Point>>();
    	temp2.add(coordinates);
    	return temp2;
    }
    
    /***
     * 
     * @param String threewords : represents the point's ww3 location.
     * @return Coordinate object with latitude and longitude corresponding to ww3
     * @throws IOException
     * @throws InterruptedException
     */
    
    private static Coordinate convertCoord(String threewords) throws IOException, InterruptedException {
    	String words[] = threewords.split("\\.");
    	String ww3uri = "http://localhost:80/words/" + words[0] + "/" + words[1]+ "/" + words[2] + "/details.json";
    	String coord =  sendHTTP(ww3uri);
    	
    	var sensorCoord = new Gson().fromJson(coord.toString(), ww3ToCoord.class);
    	return sensorCoord.coordinates;
    }
    
    /***
     * 
     * @param uri: uri required to send request
     * @return String response to the HTTP request
     * @throws IOException
     * @throws InterruptedException
     */
    private static String sendHTTP(String uri) throws IOException, InterruptedException {
    	HttpClient client = HttpClient.newHttpClient();
    	HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-type", "application/json")
                .build();
          HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
          return response.body();
    }
    
    /***
     * @implNote calculates the euclidean distance between two points
     * @param point1
     * @param point2
     * @return distance[] - BigDecimal array that contains sqrt( diffx^2 + diffy^2)
     */
    public static double euclidDist(double[] point1, double[] point2) {
    	double distance = Math.pow((point1[0] - point2[0]),2) + Math.pow((point1[1] - point2[1]), 2);
    	return Math.sqrt(distance);
//    	MathContext precision = new MathContext(8);
//    	return distance.sqrt(precision);
    }
    
    private static void writeGeoJSON(FeatureCollection path) {
    	try {
    		File f = new File("heatmap.geojson");
            FileWriter file = new FileWriter(f);
            file.write(path.toJson());
            file.close();
         } catch (IOException e) {
            e.printStackTrace();
       }
    }
}

