package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

import java.lang.reflect.Type;

import com.google.gson.reflect.TypeToken;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;

class WW3ToCoord{
	private String words;
	Coordinate coordinates;
	public String getWords() {
		return words;
	}
}

class SensorReadings{
	private String location;
	private double battery;
	private String reading;
	Coordinate coordinates;
	private boolean isRead = false;
	
	public String getLocation() {
		return location;
	}
	public double getBattery() {
		return battery;
	}
	
	public String getReading() {
		return reading;
	}
	
	public void setIsRead(boolean isRead) {
		this.isRead = isRead;
	}
	
	public boolean getIsRead() {
		return isRead;
	}
}

class Coordinate {
	private double lng;
	private double lat;
	public double getLatitude() {
		return lat;
	}
	public double getLongitude() {
		return lng;
	}
}

/**
 * Things to keep in hand
 * Run WebServer: java -jar WebServerLite.jar
 * Run aqmaps: java -jar aqmaps-0.0.1-SNAPSHOT.jar 15 06 2021 55.9444 -3.1878 5678 80
 * Turf documentation : https://docs.mapbox.com/android/java/overview/turf/#working-with-geojson
 * 						https://docs.mapbox.com/archive/android/java/api/libjava-turf/4.0.0/com/mapbox/turf/TurfJoins.html
 * 
 * tricky ones:
 * 
 * java -jar aqmaps-0.0.1-SNAPSHOT.jar 01 01 2020 55.9460 -3.1858 5678 80
 * java -jar aqmaps-0.0.1-SNAPSHOT.jar 12 12 2020 55.9428 -3.1868 5678 80
 * 
 * tricky starting points: 
 * - one side of appleton: 55.9441 -3.1870
 * - inside that nook in forum: 55.9449 -3.1870
 * - bottom left corner of map: 55.942617 -3.192473
 */						


public class DroneAlgorithm extends HelperFunctions
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
    	
    	final var long_W = (-3.192473);
    	final var long_E = (-3.184319);
    	final var lat_N = (55.946233);
    	final var lat_S = (55.942617);
    	Polygon map = Polygon.fromLngLats(constructMap(lat_N, lat_S, long_E, long_W));
    	
    	final var airQualityDataUri = "http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json";
    	final var noFlyZonesUri = "http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson";
        
    	final String currentSensorReadings = sendHTTP(airQualityDataUri);
    	final String noFlyZones = sendHTTP(noFlyZonesUri);
    	
        final var startingPoint = Point.fromLngLat(Double.parseDouble(args[4]), Double.parseDouble(args[3]));
        
        Type listOfSensors = new TypeToken<ArrayList<SensorReadings>>() {}.getType();
        ArrayList<SensorReadings> sensorData = new Gson().fromJson(currentSensorReadings, listOfSensors);
        
        for(SensorReadings sensor : sensorData) {
        	sensor.coordinates = WW3ToCoordinates(sensor.getLocation()); //these are all the readings from one day 
        }
        
        var listOfBuildings = new ArrayList<Feature>(FeatureCollection.fromJson(noFlyZones.toString()).features());
        var mapOfNoFlyZones = new HashMap<String, Polygon>();
        
        for(Feature oneZone : listOfBuildings) {
        	String name = oneZone.properties().get("name").toString();
        	mapOfNoFlyZones.put(name.substring(1, name.length()-1), (Polygon)oneZone.geometry());
        }
        
        var sensorMarkers = new ArrayList<Feature>();
        var dronePath = new ArrayList<Point>();
        dronePath.add(startingPoint);
        		
        /*MAIN ALGO */
        double[] currPoint = {startingPoint.latitude(), startingPoint.longitude()};
        String droneMoves = "";
        var moveCount = 0;
        double[] previousMove = {startingPoint.latitude(), startingPoint.longitude()};
        double[] twoMovesBack = {startingPoint.latitude(), startingPoint.longitude()};
        
        droneSearchAlgorithm:while(moveCount <= 150) {
        	System.out.println("\n\nMove " + moveCount);
        	var closestIndex = findClosestSensor(currPoint, sensorData);
        	var closestPoint = new double[2];
        	if(closestIndex == -1) {
        		/*no more sensors left - need to go back to starting point */
        		closestPoint[0] = startingPoint.latitude();
        		closestPoint[1] = startingPoint.longitude();
        	}
        	else {
        		closestPoint[0] = sensorData.get(closestIndex).coordinates.getLatitude();
        		closestPoint[1]= sensorData.get(closestIndex).coordinates.getLongitude();
        	} 
        	
        	var latDiff = closestPoint[0] - currPoint[0];
        	var longDiff = closestPoint[1] - currPoint[1];
 
        	var radianTheta = Math.atan2(latDiff, longDiff);
        	var degreeTheta = Math.toDegrees(radianTheta);
        	if(degreeTheta < 0) {
        		degreeTheta = 360 + degreeTheta;
        	}
        	degreeTheta =  10 * Math.round(degreeTheta/10);
        	radianTheta = Math.toRadians(degreeTheta);
        	droneMoves = droneMoves + (moveCount+1) + "," + currPoint[0] + "," + currPoint[1] + ",";
   
        	double[] newPointPositive = findDirectionOfNextMove("+", degreeTheta, currPoint, twoMovesBack, mapOfNoFlyZones, map);
        	double[] newPointNegative = findDirectionOfNextMove("-", degreeTheta, currPoint, twoMovesBack, mapOfNoFlyZones, map);
        	
        	if(euclidianDistance(newPointPositive,closestPoint) <= euclidianDistance(newPointNegative, closestPoint)) {
        		currPoint[0] = newPointPositive[0];
            	currPoint[1] = newPointPositive[1];
            	degreeTheta = newPointPositive[2];
        	}
        	else {
        		currPoint[0] = newPointNegative[0];
            	currPoint[1] = newPointNegative[1];
            	degreeTheta = newPointNegative[2];
        	}
        	
        	droneMoves = droneMoves + (int)degreeTheta + ",";
        	dronePath.add(Point.fromLngLat(currPoint[1], currPoint[0]));
        	droneMoves = droneMoves + currPoint[0] + "," + currPoint[1] + ",";
        	if(euclidianDistance(currPoint, closestPoint) < 0.0002) {
        		//if we are near the starting point (should be 0.0003 but ok).
        		if(closestPoint[0] == startingPoint.latitude() && closestPoint[1] == startingPoint.longitude()) {
        			droneMoves = droneMoves + "null\n";
            		break droneSearchAlgorithm;
        		}
        		sensorData.get(closestIndex).setIsRead(true);
        		droneMoves = droneMoves + sensorData.get(closestIndex).getLocation() + "\n";
        		Point sensorMarker = Point.fromLngLat(closestPoint[1], closestPoint[0]);
        		Feature sensorFeature = Feature.fromGeometry(sensorMarker);
        		String[] markerProps = findMarkerProperties(sensorData.get(closestIndex).getBattery(), sensorData.get(closestIndex).getReading());
        		sensorFeature.addStringProperty("marker-color", markerProps[0]);
        		sensorFeature.addStringProperty("marker-symbol", markerProps[1]);
        		sensorFeature.addStringProperty("location", sensorData.get(closestIndex).getLocation());
        		sensorMarkers.add(sensorFeature);
        	}
        	else {
        		droneMoves = droneMoves + "null\n";
        	}	
        	twoMovesBack[0] = previousMove[0];
        	twoMovesBack[1] = previousMove[1];
        	previousMove[0] = currPoint[0];
        	previousMove[1] = currPoint[1];
        	moveCount= moveCount+1;
        }//end of droneSearchAlgorithm loop  
        
        for(SensorReadings sensor : sensorData) {
        	if(!(sensor.getIsRead())) {
        		Point unreadSensor = Point.fromLngLat(sensor.coordinates.getLongitude(), sensor.coordinates.getLatitude());
        		Feature markerUnread = Feature.fromGeometry(unreadSensor);
        		markerUnread.addStringProperty("location", sensor.getLocation());
        		sensorMarkers.add(markerUnread);
        	}
        }
        
        sensorMarkers.add(Feature.fromGeometry(LineString.fromLngLats(dronePath)));
        FeatureCollection finalPath = FeatureCollection.fromFeatures(sensorMarkers);
        writeToFile(finalPath, args[0], args[1], args[2]);
        writeToFile(droneMoves, args[0], args[1], args[2]);
        
    }//end of main
}