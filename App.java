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
 * 
 * THINGS TO BE ADDED (27TH NOV AT 1AM):
 * - TRY MANUALLY CALCULATING ATAN()
 * - Some moves go over the flyzone + add a feature of subtracting 0.0001 from the direction (that might be shorter) 
 * (above thing needed for 12/12/2020 from our og starting point)
 * -sensor points don't match the points being plotted after calculation (need to move <0.0003)
 * - need to round off degree to nearest 10s (path becomes a little crooked)
 * - add directions to the flightpath.txt string
 * - need to check cases on the Piazza page
 * - the route doesn't end exactly at the starting point - so need to make it exactly there
 */						
class Coordinate{
	double lng;
	double lat;
}

class ww3ToCoord{
	String words;
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
//    	File afile = new File("map.geojson");
//        FileWriter file = new FileWriter(afile);
//        file.write(map.toJson());
//        file.close();

    	
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
        		if(currDist <= 0.0002) {
        			one.isRead = true;
        			break;
        		}
        		if(minDist > currDist) {
        			minDist = currDist;
        			closestIndex = readings.indexOf(one);
        		}
        	} // found the closest sensor 
        	double[] closestPoint = new double[2];
        	if(closestIndex == -1) {
        		/*no more sensors left - need to go back to starting point */
        		closestPoint[0] = start.latitude();
        		closestPoint[1] = start.longitude();
        	}
        	else {
        		closestPoint[0] = readings.get(closestIndex).coordinates.lat;
        		closestPoint[1]= readings.get(closestIndex).coordinates.lng;
        	} 
        	
        	//somehow only taking it as longdiff, latdiff works....
        	var latDiff = closestPoint[0] - currPoint[0];
        	var longDiff = closestPoint[1]-currPoint[1];
 
        	var radianTheta = Math.atan2(longDiff, latDiff);
        	var degreeTheta = Math.toDegrees(radianTheta);
//        	System.out.println("Radians - output of atan2: " + radianTheta);
//        	degreeTheta = fitDirection(degreeTheta);
        	System.out.println(degreeTheta);
        	System.out.println(Math.toRadians(degreeTheta));
        	move = move + (moveCount+1) + "," + currPoint[0] + "," + currPoint[1] + "," + fitDirection(degreeTheta) + ",";
        	
        	var noFly = true;
        	var temp_lat = currPoint[0];
        	var temp_long = currPoint[1];
        	while(noFly == true) {
        		temp_lat = currPoint[0] + Math.cos(radianTheta)*(0.0003);
            	temp_long = currPoint[1] + Math.sin(radianTheta)*(0.0003);
            	loop_buildingcheck: for(String keys : buildings.keySet()){
            		if(TurfJoins.inside(Point.fromLngLat(temp_long, temp_lat), buildings.get(keys)) || !(TurfJoins.inside(Point.fromLngLat(temp_long, temp_lat), map))) {
            			radianTheta = radianTheta + 0.0001;
            			noFly = true;
            			break loop_buildingcheck;
            		}
            		noFly = false;
            	}
            	if(noFly == false) {
            		if(throughNoFlyZone(currPoint, radianTheta, buildings)) {
            			radianTheta = radianTheta + 0.0001;
            			noFly = true;
            		}
            	}
        	}
        	currPoint[0] = temp_lat;
        	currPoint[1] = temp_long;
        	
        	line_points.add(Point.fromLngLat(currPoint[1], currPoint[0]));
        	
        	move = move + currPoint[0] + "," + currPoint[1] + ",";
        	if(euclidDist(currPoint, closestPoint) < 0.0002) {
        		//if we are near the starting point (should be 0.0003 but ok).
        		if(closestPoint[0] == start.latitude() && closestPoint[1] == start.longitude()) {
            		break;
        		}
        		readings.get(closestIndex).isRead = true;
        		move = move + readings.get(closestIndex).location + "\n";
        		//adding marker on the sensor that is being read
        		Point sensorMarker = Point.fromLngLat(closestPoint[1], closestPoint[0]);
        		Feature sensorFeature = Feature.fromGeometry(sensorMarker);
        		String[] markerProps = findMarkerProperties(readings.get(closestIndex).battery, readings.get(closestIndex).reading);
        		sensorFeature.addStringProperty("marker-color", markerProps[0]);
        		sensorFeature.addStringProperty("marker-symbol", markerProps[1]);
        		sensorFeature.addStringProperty("location", readings.get(closestIndex).location);
        		markers.add(sensorFeature);
        	}
        	else {
        		move = move + "null\n";
        	}	
        	moveCount= moveCount+1;
        }//end of while loop  
        
        for(SensorReadings sensor : readings) {
        	if(!(sensor.isRead)) {
        		Point unreadSensor = Point.fromLngLat(sensor.coordinates.lng, sensor.coordinates.lat);
        		Feature markerUnread = Feature.fromGeometry(unreadSensor);
        		markerUnread.addStringProperty("location", sensor.location);
        		markers.add(markerUnread);
        	}
        }
        
        markers.add(Feature.fromGeometry(LineString.fromLngLats(line_points)));
        FeatureCollection finalPath = FeatureCollection.fromFeatures(markers);
        writeToFile(finalPath, args[0], args[1], args[2]);
        writeToFile(move, args[0], args[1], args[2]);
    }
    
    private static String[] findMarkerProperties(double battery, String reading) {
    	String[] props = new String[2];
    	double reading_n = 999;
    	if(reading.equals("null") || reading.equals("NaN")) {
    		battery = 0;
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
    		props[0] = "#ff8000";
    		props[1] = "danger";
    	}
    	else if(reading_n >= 192 && reading_n < 224) {
    		props[0] = "#ff4000";
    		props[1] = "danger";
    	}
    	else if(reading_n >= 224 && reading_n < 256) {
    		props[0] = "#ff0000";
    		props[1] = "danger";
    	}
    	return props;
    }
    
    
    private static double fitDirection(double direction) {
    	if(direction < 0) {
    		direction = Math.abs(direction - 90);
    	}
    	else {
    		direction = Math.round(360 - (direction - 90));
    		
    	}
    	if(direction >= 360) {
    		return direction%360;
    	}
    	return Math.round(direction);
//    	if(direction >= 5.0 && direction < 15.0) {
//    		return 10;
//    	}
//    	else if(direction >= 15.0 && direction < 25.0) {
//    		return 20;
//    	}
//    	else if(direction >= 25 && direction < 35) {
//    		return 30;
//    	}
//    	else if(direction >= 35 && direction < 45) {
//    		return 40;
//    	}
//    	else if(direction >= 45 && direction < 55) {
//    		return 50;
//    	}
//    	else if(direction >= 55 && direction < 65) {
//    		return 60;
//    	}
//    	else if(direction >= 65 && direction < 75) {
//    		return 70;
//    	}
//    	else if(direction >= 75 && direction < 85) {
//    		return 80;
//    	}
//    	else if(direction >= 85 && direction < 95) {
//    		return 90;
//    	}
//    	else if(direction >= 95 && direction < 105) {
//    		return 100;
//    	}
//    	else if(direction >= 105 && direction < 115){
//    		return 110;
//    	}
//    	else if(direction >= 115 && direction < 125){
//    		return 120;
//    	}
//    	else if(direction >= 125 && direction < 135){
//    		return 130;
//    	}
//    	else if(direction >= 135 && direction < 145){
//    		return 140;
//    	}
//    	else if(direction >= 145 && direction < 155){
//    		return 150;
//    	}
//    	else if(direction >= 155 && direction < 165){
//    		return 160;
//    	}
//    	else if(direction >= 165 && direction < 175){
//    		return 170;
//    	}
//    	else if(direction >= 175 && direction <= 180 || direction < -175){
//    		return 180;
//    	}
//    	else if(direction < -165 && direction >= -175) {
//    		return 190;
//    	}
//    	else if(direction < -155 && direction >= -165) {
//    		return 200;
//    	}
//    	else if(direction < -145 && direction >= -155) {
//    		return 210;
//    	}
//    	else if(direction < -135 && direction >= -145) {
//    		return 220;
//    	}
//    	else if(direction < -125 && direction >= -135) {
//    		return 230;
//    	}
//    	else if(direction < -115 && direction >= -125) {
//    		return 240;
//    	}
//    	else if(direction < -105 && direction >= -115) {
//    		return 250;
//    	}
//    	else if(direction < -95 && direction >= -105) {
//    		return 260;
//    	}
//    	else if(direction < -85 && direction >= -95) {
//    		return 270;
//    	}
//    	else if(direction < -75 && direction >= -85) {
//    		return 280;
//    	}
//    	else if(direction < -65 && direction >= -75) {
//    		return 290;
//    	}
//    	else if(direction < -55 && direction >= -65) {
//    		return 300;
//    	}
//    	else if(direction < -45 && direction >= -55) {
//    		return 310;
//    	}
//    	else if(direction < -35 && direction >= -45) {
//    		return 320;
//    	}
//    	else if(direction < -25 && direction >= -35) {
//    		return 330;
//    	}
//    	else if(direction < -15 && direction >= -25) {
//    		return 340;
//    	}
//    	else if(direction < -5 && direction >= -15) {
//    		return 350;
//    	}
//    	else
//    		return 0;
    }
    
    private static boolean throughNoFlyZone(double[] point1, double direction, Map<String, Polygon> buildings) {
    	for(double i = 0.00001; i < 0.0003; i += 0.00001) {
    		var temp_lat = point1[0] + Math.cos(direction)*(i);
        	var temp_long = point1[1] + Math.sin(direction)*(i);
        	
        	for(String keys : buildings.keySet()){
        		if(TurfJoins.inside(Point.fromLngLat(temp_long, temp_lat), buildings.get(keys))) {
        			return true;
        		}
        	}
        	
    	}
    	return false;
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
    
    private static void writeToFile(FeatureCollection path, String date, String month, String year) {
    	try {
    		File f = new File("readings-"+date+"-"+month+"-"+year+".geojson");
            FileWriter file = new FileWriter(f);
            file.write(path.toJson());
            file.close();
         } catch (IOException e) {
            e.printStackTrace();
       }
    }
    
    private static void writeToFile(String moves, String date, String month, String year) {
    	try {
    		File f = new File("flightpath-"+date+"-"+month+"-"+year+".txt");
            FileWriter file = new FileWriter(f);
            file.write(moves);
            file.close();
         } catch (IOException e) {
            e.printStackTrace();
       }
    }
}

