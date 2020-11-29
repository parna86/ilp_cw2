package uk.ac.ed.inf.aqmaps;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;

public class helperFunctions {
	 protected static String[] findMarkerProperties(double battery, String reading) {
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
	    
	 	protected static boolean throughNoFlyZone(double[] point1, double direction, Map<String, Polygon> buildings) {
	    	for(double i = 0.000005; i < 0.0003; i += 0.000005) {
	    		var temp_lat = point1[0] + Math.sin(direction)*(i);
	        	var temp_long = point1[1] + Math.cos(direction)*(i);
	        	
	        	for(Polygon keys : buildings.values()){
	        		if(TurfJoins.inside(Point.fromLngLat(temp_long, temp_lat), keys)) {
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
	    
	 	protected static List<List<Point>> constructMap(double north, double south, double east, double west){
	    	
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
	    
	 	protected static Coordinate WW3ToCoordinates(String threewords) throws IOException, InterruptedException {
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
	 	protected static String sendHTTP(String uri) throws IOException, InterruptedException {
	    	var client = HttpClient.newHttpClient();
	    	final var request = HttpRequest.newBuilder()
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
	 	protected static double euclidianDistance(double[] point1, double[] point2) {
	    	final var distance = Math.pow((point1[0] - point2[0]),2) + Math.pow((point1[1] - point2[1]), 2);
	    	return Math.sqrt(distance);
	    }
	    
	 	protected static void writeToFile(FeatureCollection path, String date, String month, String year) {
	    	try {
	    		final File newFile = new File("readings-"+date+"-"+month+"-"+year+".geojson");
	            var fileWrite = new FileWriter(newFile);
	            fileWrite.write(path.toJson());
	            fileWrite.close();
	         } catch (IOException error) {
	            error.printStackTrace();
	       }
	    }
	    
	 	protected static void writeToFile(String moves, String date, String month, String year) {
	    	try {
	    		final File newFile = new File("flightpath-"+date+"-"+month+"-"+year+".txt");
	            FileWriter fileWrite = new FileWriter(newFile);
	            fileWrite.write(moves);
	            fileWrite.close();
	         } catch (IOException error) {
	            error.printStackTrace();
	       }
	    }
	    
	 	protected static int findClosestSensor(double[] currentLocation, ArrayList<SensorReadings> sensorData) {
	    	var closestIndex = -1;
	    	var minDist = 999.0;
	    	for(SensorReadings currentSensor: sensorData) {
	    		double[] sensorPoint = {currentSensor.coordinates.getLatitude(), currentSensor.coordinates.getLongitude()};
	    		if(currentSensor.getIsRead()) {
	    			continue;
	    		}
	    		var currDist = euclidianDistance(currentLocation, sensorPoint);
	    		if(minDist > currDist) {
	    			minDist = currDist;
	    			closestIndex = sensorData.indexOf(currentSensor);
	    		}
	    	} 
	    	return closestIndex;
	    }
}
