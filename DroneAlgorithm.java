package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import com.google.gson.Gson;

import com.google.gson.reflect.TypeToken;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;


class SensorReadings extends HelperFunctions{
	private String location;
	private double battery;
	private String reading;
	Coordinate coordinates;
	private boolean isRead = false;
	
	public void ww3ToCoord() throws IOException, InterruptedException{
		String words[] = location.split("\\.");
    	String ww3uri = "http://localhost:80/words/" + words[0] + "/" + words[1]+ "/" + words[2] + "/details.json";
    	String coord = sendHTTP(ww3uri);
    	var sensorCoord = new Gson().fromJson(coord.toString(), WW3ToCoord.class);
    	this.coordinates = sensorCoord.coordinates;
	}
	
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
	
	class WW3ToCoord{
		Coordinate coordinates;
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
    	
    	final var longW = (-3.192473);
    	final var longE = (-3.184319);
    	final var latN = (55.946233);
    	final var latS = (55.942617);
    	Polygon map = Polygon.fromLngLats(constructMap(latN, latS, longE, longW));
    	
    	final var airQualityDataUri = "http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json";
    	final var noFlyZonesUri = "http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson";
        
    	final String currentSensorReadings = sendHTTP(airQualityDataUri);
    	final String noFlyZones = sendHTTP(noFlyZonesUri);
    	System.out.println(currentSensorReadings);
        final var startingPoint = Point.fromLngLat(Double.parseDouble(args[4]), Double.parseDouble(args[3]));
        
        ArrayList<SensorReadings> sensorData = new Gson().fromJson(currentSensorReadings, new TypeToken<ArrayList<SensorReadings>>() {}.getType());
        
        for(SensorReadings sensor : sensorData) {
        	sensor.ww3ToCoord(); //these are all the readings from one day 
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
        
        droneSearchAlgorithm: while(moveCount < 150) {
        	
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
        	
        	if(euclideanDistance(newPointPositive,closestPoint) <= euclideanDistance(newPointNegative, closestPoint)) {
        		currPoint[0] = newPointPositive[0];
            	currPoint[1] = newPointPositive[1];
            	degreeTheta = newPointPositive[2];
        	}
        	else {
        		currPoint[0] = newPointNegative[0];
            	currPoint[1] = newPointNegative[1];
            	degreeTheta = newPointNegative[2];
        	}
        	
        	dronePath.add(Point.fromLngLat(currPoint[1], currPoint[0]));
        	
        	droneMoves = droneMoves + (int)degreeTheta + ","+ currPoint[0] + "," + currPoint[1] + ",";
        	
        	if(euclideanDistance(currPoint, closestPoint) < 0.0002) {
        		//if we are near the starting point (should be 0.0003 but ok).
        		if(closestPoint[0] == startingPoint.latitude() && closestPoint[1] == startingPoint.longitude()) {
        			droneMoves = droneMoves + "null\n";
            		break droneSearchAlgorithm;
        		}
        		sensorData.get(closestIndex).setIsRead(true);
        		droneMoves = droneMoves + sensorData.get(closestIndex).getLocation() + "\n";
        		var visitedSensor = Feature.fromGeometry(Point.fromLngLat(closestPoint[1], closestPoint[0]));
        		String[] markerProps = findMarkerProperties(sensorData.get(closestIndex).getBattery(), sensorData.get(closestIndex).getReading());
        		visitedSensor.addStringProperty("marker-color", markerProps[0]);
        		visitedSensor.addStringProperty("marker-symbol", markerProps[1]);
        		visitedSensor.addStringProperty("location", sensorData.get(closestIndex).getLocation());
        		sensorMarkers.add(visitedSensor);
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
        		Feature unreadSensor = Feature.fromGeometry(Point.fromLngLat(sensor.coordinates.getLongitude(), sensor.coordinates.getLatitude()));
        		unreadSensor.addStringProperty("location", sensor.getLocation());
        		sensorMarkers.add(unreadSensor);
        	}
        }
        
        sensorMarkers.add(Feature.fromGeometry(LineString.fromLngLats(dronePath)));
        FeatureCollection finalPath = FeatureCollection.fromFeatures(sensorMarkers);
        writeToFile(finalPath, args[0], args[1], args[2]);
        writeToFile(droneMoves, args[0], args[1], args[2]);
        
    }//end of main
}