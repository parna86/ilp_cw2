package uk.ac.ed.inf.aqmaps;

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
import java.math.BigDecimal;
import java.math.MathContext;

import com.google.gson.reflect.TypeToken;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
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
    	final var long_W = new BigDecimal(-3.192473);
    	final var long_E = new BigDecimal(-3.184319);
    	final var lat_N = new BigDecimal(55.946233);
    	final var lat_S = new BigDecimal(55.942617);
    	Polygon map = Polygon.fromLngLats(constructMap(lat_N, lat_S, long_E, long_W));
    	
    	
    	String AQuri = "http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json";
    	String BDuri = "http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson";
        
    	String currReadings = sendHTTP(AQuri);
    	String noFlyZones = sendHTTP(BDuri);
    	
    	
        BigDecimal startingLat = new BigDecimal(args[3]);
        BigDecimal startingLong = new BigDecimal(args[4]);
        var start = Point.fromLngLat(startingLong.doubleValue(), startingLat.doubleValue());
        
        
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
        /*MAIN ALGO */
        String move = "";
        var moveCount = 0;
        while(moveCount <= 150) {
        	BigDecimal minDist = new BigDecimal("10");
        	int closestIndex = -1;
        	for(SensorReadings one: readings) {
        		BigDecimal[] currPoint = {new BigDecimal(start.latitude()), new BigDecimal(start.longitude())};
        		BigDecimal[] sensorPoint = {new BigDecimal(one.coordinates.lat), new BigDecimal(one.coordinates.lng)};
        		var currDist = euclidDist(currPoint, sensorPoint);
        		System.out.println("Distance calculated by my function:" + currDist);
        		System.out.println("minDist val: " + minDist);
        		System.out.println("------");
        		System.out.println("Starting point:" + currPoint[0]);
        		System.out.println("Sensor Point: " + sensorPoint[0]);
        		if(minDist.compareTo(currDist) == 1) {
        			//if minDist > currDist
        			minDist = currDist;
        			closestIndex = readings.indexOf(one);
        			System.out.println("Closest point updated to idx " + closestIndex);
        		}
        	}
        	System.out.println(closestIndex);
        	BigDecimal[] closestPoint = {new BigDecimal(readings.get(closestIndex).coordinates.lat), new BigDecimal(readings.get(closestIndex).coordinates.lng)};
        	System.out.println("Closest sensor point to curr point:" + closestPoint[0] + "," + closestPoint[1]);
        	//would've found potentially closest sensor
        	//now calculate the direction of this point - this gives direction but we have to think about how to take the 
        	//multiple of 10 into account
        	var direction = Math.atan2(closestPoint[1].subtract(new BigDecimal(start.longitude())).doubleValue(), closestPoint[0].subtract(new BigDecimal(start.latitude())).doubleValue());
        	System.out.println("Direction of movement:"+ direction);
        	System.out.println("New point:" + Math.sin(direction)*(0.0002) + "," + Math.cos(direction)*(0.0002));
        	moveCount= moveCount+500;
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
    
    private static List<List<Point>> constructMap(BigDecimal north, BigDecimal south, BigDecimal east, BigDecimal west){
    	BigDecimal lat_diff = north.subtract(south);
    	BigDecimal long_diff = east.subtract(west);
    	lat_diff = lat_diff.divide(new BigDecimal(10));
    	long_diff = long_diff.divide(new BigDecimal(10));
    	
    	/**
    	 * ArrayList of coordinates below. Need to create that for individual polygons too.
    	 */
    	final var coordinates = new ArrayList<Point>();
    	coordinates.add(Point.fromLngLat(west.doubleValue(), north.doubleValue()));
    	coordinates.add(Point.fromLngLat(east.doubleValue(), north.doubleValue()));
    	coordinates.add(Point.fromLngLat(east.doubleValue(), south.doubleValue()));
    	coordinates.add(Point.fromLngLat(west.doubleValue(), south.doubleValue()));
    	coordinates.add(Point.fromLngLat(west.doubleValue(), north.doubleValue()));
    	
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
    public static BigDecimal euclidDist(BigDecimal[] point1, BigDecimal[] point2) {
    	BigDecimal distance =  (point1[0].subtract(point2[0])).add(point1[1].subtract(point2[1])).pow(2);
    	MathContext precision = new MathContext(8);
    	return distance.sqrt(precision);
    }
}

