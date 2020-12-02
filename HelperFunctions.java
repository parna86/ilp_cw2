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

import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;

public class HelperFunctions {
	
	 protected static double[] findDirectionOfNextMove(String plusOrMinus, double degreeTheta, double[] currPoint, double[] twoMovesBack, Map<String, Polygon> mapOfNoFlyZones, Polygon map) {
		 var noFly = true;
		 var newLatitude = currPoint[0];
		 var newLongitude = currPoint[1];
		 var radianTheta = Math.toRadians(degreeTheta);
		 while(noFly == true) {
			 newLatitude = currPoint[0] + Math.sin(radianTheta)*(0.0003);
			 newLongitude = currPoint[1] + Math.cos(radianTheta)*(0.0003);
         	insideNoFlyZoneCheck: for(Polygon oneZone : mapOfNoFlyZones.values()){
         		if(TurfJoins.inside(Point.fromLngLat(newLongitude, newLatitude), oneZone) || !(TurfJoins.inside(Point.fromLngLat(newLongitude, newLatitude), map))) {
         			if(plusOrMinus.equals("+")) {
         				degreeTheta += 10;
             			if(degreeTheta >= 360){
             				degreeTheta = degreeTheta % 360;
             			}
         			}
         			else if(plusOrMinus.equals("-")){
         				degreeTheta -= 10;
            			if(degreeTheta < 0){
            				degreeTheta = degreeTheta + 360;
            			}
         			}
         			radianTheta = Math.toRadians(degreeTheta);
         			noFly = true;
         			break insideNoFlyZoneCheck;
         		}
         		noFly = false;
         	}
         	if(noFly == false) {
         		if(throughNoFlyZone(currPoint, radianTheta, mapOfNoFlyZones) || (twoMovesBack[0] == newLatitude && twoMovesBack[1] == newLongitude)) {
         			if(plusOrMinus == "+") {
         				degreeTheta += 10;
             			if(degreeTheta >= 360){
             				degreeTheta = degreeTheta % 360;
             			}
         			}
         			else if(plusOrMinus == "-"){
         				degreeTheta -= 10;
            			if(degreeTheta < 0){
            				degreeTheta = degreeTheta + 360;
            			}
         			}
         			radianTheta = Math.toRadians(degreeTheta);
         			noFly = true;
         		}
         	}
     	}
		 double[] newPoint = {newLatitude, newLongitude, degreeTheta};
		 return newPoint;
	 }
	 
	 
	 protected static String[] findMarkerProperties(double battery, String reading) {
	    	String[] markerProperties = new String[2];
	    	double readingValue = 999;
	    	if(reading.equals("null") || reading.equals("NaN")) {
	    		battery = 0;
	    	}
	    	else {
	    		readingValue = Double.parseDouble(reading);
	    	}
	    	// 0 - colour, 1 - marker
	    	if(battery < 10.0) {
	    		markerProperties[0] = "#000";
	    		markerProperties[1] = "cross";
	    	}
	    	else if(readingValue >= 0 && readingValue < 32) {
	    		markerProperties[0] = "#00ff00";
	    		markerProperties[1] = "lighthouse";
	    	}
	    	else if(readingValue >= 32 && readingValue < 64) {
	    		markerProperties[0] = "#40ff00";
	    		markerProperties[1] = "lighthouse";
	    	}
	    	else if(readingValue >= 64 && readingValue < 96) {
	    		markerProperties[0] = "#80ff00";
	    		markerProperties[1] = "lighthouse";
	    	}
	    	else if(readingValue >= 96 && readingValue < 128) {
	    		markerProperties[0] = "#c0ff00";
	    		markerProperties[1] = "lighthouse";
	    	}
	    	else if(readingValue >= 128 && readingValue < 160) {
	    		markerProperties[0] = "#ffc000";
	    		markerProperties[1] = "danger";
	    	}
	    	else if(readingValue >= 160 && readingValue < 192) {
	    		markerProperties[0] = "#ff8000";
	    		markerProperties[1] = "danger";
	    	}
	    	else if(readingValue >= 192 && readingValue < 224) {
	    		markerProperties[0] = "#ff4000";
	    		markerProperties[1] = "danger";
	    	}
	    	else if(readingValue >= 224 && readingValue < 256) {
	    		markerProperties[0] = "#ff0000";
	    		markerProperties[1] = "danger";
	    	}
	    	return markerProperties;
	    }
	    
	 	protected static boolean throughNoFlyZone(double[] point1, double direction, Map<String, Polygon> buildings) {
	    	for(double i = 0.000005; i < 0.0003; i += 0.000005) {
	    		var newLatitude = point1[0] + Math.sin(direction)*(i);
	        	var newLongitude = point1[1] + Math.cos(direction)*(i);
	        	
	        	for(Polygon keys : buildings.values()){
	        		if(TurfJoins.inside(Point.fromLngLat(newLongitude, newLatitude), keys)) {
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
	    	
	    	List<List<Point>> map = new ArrayList<List<Point>>();
	    	map.add(coordinates);
	    	return map;
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
	 	protected static double euclideanDistance(double[] point1, double[] point2) {
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
	    		var currDist = euclideanDistance(currentLocation, sensorPoint);
	    		if(minDist > currDist) {
	    			minDist = currDist;
	    			closestIndex = sensorData.indexOf(currentSensor);
	    		}
	    	} 
	    	return closestIndex;
	    }
}
