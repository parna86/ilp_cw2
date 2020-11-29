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
 * tricky ones:
 * 
 * java -jar aqmaps-0.0.1-SNAPSHOT.jar 01 01 2020 55.9460 -3.1858 5678 80
 * java -jar aqmaps-0.0.1-SNAPSHOT.jar 12 12 2020 55.9428 -3.1868 5678 80
 * 
 * TRY OUT TAKING THE NEAREST MOVE TO THE CLOSEST SENSOR POINT. 
 * BREADTH FIRST SEARCH
 */						

class Coordinate{
	private double lng;
	private double lat;
	public double getLatitude() {
		return lat;
	}
	public double getLongitude() {
		return lng;
	}
}

class ww3ToCoord{
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
    	
    	String airQualityDataUri = "http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json";
    	String noFlyZonesUri = "http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson";
        
    	String currReadings = sendHTTP(airQualityDataUri);
    	String noFlyZones = sendHTTP(noFlyZonesUri);
    	
        var startingPoint = Point.fromLngLat(Double.parseDouble(args[4]), Double.parseDouble(args[3]));
        
        
        
        
        
        Type listOfSensors = new TypeToken<ArrayList<SensorReadings>>() {}.getType();
        ArrayList<SensorReadings> sensorData = new Gson().fromJson(currReadings, listOfSensors);
        
        
        
        
        
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
        	
        	//somehow only taking it as longdiff, latdiff works....
        	var latDiff = closestPoint[0] - currPoint[0];
        	var longDiff = closestPoint[1] - currPoint[1];
 
        	var radianTheta = Math.atan2(latDiff, longDiff);
        	var degreeTheta = Math.toDegrees(radianTheta);
        	System.out.println(degreeTheta);
        	if(degreeTheta < 0) {
        		degreeTheta = 360 + degreeTheta;
        	}
        	degreeTheta =  10 * Math.round(degreeTheta/10);
        	radianTheta = Math.toRadians(degreeTheta);
        	System.out.println(degreeTheta);
        	droneMoves = droneMoves + (moveCount+1) + "," + currPoint[0] + "," + currPoint[1] + ",";
        	var temp_degree = degreeTheta;
        	var noFly = true;
        	var temp_lat = currPoint[0];
        	var temp_long = currPoint[1];
        	while(noFly == true) {
        		temp_lat = currPoint[0] + Math.sin(radianTheta)*(0.0003);
            	temp_long = currPoint[1] + Math.cos(radianTheta)*(0.0003);
            	System.out.println("\n\nIncrementing:");
            	System.out.println(temp_lat +"," + temp_long);
            	System.out.println("Degree: " + degreeTheta);
            	insideNoFlyZoneCheck: for(Polygon oneZone : mapOfNoFlyZones.values()){
            		if(TurfJoins.inside(Point.fromLngLat(temp_long, temp_lat), oneZone) || !(TurfJoins.inside(Point.fromLngLat(temp_long, temp_lat), map))) {
            			degreeTheta += 10;
            			if(degreeTheta >= 360){
            				degreeTheta = degreeTheta % 360;
            			}
            			radianTheta = Math.toRadians(degreeTheta);
            			noFly = true;
            			break insideNoFlyZoneCheck;
            		}
            		noFly = false;
            	}
            	if(noFly == false) {
            		System.out.println("Curr Point:");
            		System.out.println(temp_lat + "," + temp_long);
            		System.out.println("Previous Point");
            		System.out.println(twoMovesBack[0] + "," + twoMovesBack[1]);
            		if(throughNoFlyZone(currPoint, radianTheta, mapOfNoFlyZones) || (twoMovesBack[0] == temp_lat && twoMovesBack[1] == temp_long)) {
            			System.out.println("Enters here");
            			degreeTheta += 10;
            			radianTheta = Math.toRadians(degreeTheta);
            			noFly = true;
            			System.out.println("Updated degree: " + degreeTheta);
            		}
            	}
        	}
        	
        	System.out.println("Inc Degree: " + degreeTheta + "\n\n");
        	
        	noFly = true;
        	var temp_lat_2 = currPoint[0];
        	var temp_long_2 = currPoint[1];
        	var finaldeg = degreeTheta;
        	degreeTheta = temp_degree;
        	radianTheta = Math.toRadians(degreeTheta);
        	while(noFly == true) {
        		temp_lat_2 = currPoint[0] + Math.sin(radianTheta)*(0.0003);
        		temp_long_2 = currPoint[1] + Math.cos(radianTheta)*(0.0003);
        		System.out.println("\n\nDecrementing:");
            	System.out.println(temp_lat_2 +"," + temp_long_2);
            	System.out.println("Degree: " + degreeTheta);
            	insideNoFlyZoneCheck: for(Polygon oneZone : mapOfNoFlyZones.values()){
            		if(TurfJoins.inside(Point.fromLngLat(temp_long_2, temp_lat_2), oneZone) || !(TurfJoins.inside(Point.fromLngLat(temp_long_2, temp_lat_2), map))) {
            			degreeTheta -= 10;
            			if(degreeTheta < 0){
            				degreeTheta = degreeTheta + 360;
            			}
            			radianTheta = Math.toRadians(degreeTheta);
            			noFly = true;
            			break insideNoFlyZoneCheck;
            		}
            		noFly = false;
            	}
            	if(noFly == false) {
            		System.out.println("Curr Point:");
            		System.out.println(temp_lat_2 + "," + temp_long_2);
            		System.out.println("Previous Point");
            		System.out.println(twoMovesBack[0] + "," + twoMovesBack[1]);
            		if(throughNoFlyZone(currPoint, radianTheta, mapOfNoFlyZones)|| (twoMovesBack[0] == temp_lat_2 && twoMovesBack[1] == temp_long_2)) {
            			degreeTheta -= 10;
            			if(degreeTheta < 0){
            				degreeTheta = degreeTheta + 360;
            			}
            			radianTheta = Math.toRadians(degreeTheta);
            			noFly = true;
            		}
            	}
        	}
        	System.out.println("Dec Degree: " + degreeTheta);
        	double[] p1 = {temp_lat, temp_long};
        	double[] p2 = {temp_lat_2, temp_long_2};
        	System.out.println("Inc dist " + euclidianDistance(p1,closestPoint));
        	System.out.println("Dec dist " + euclidianDistance(p2, closestPoint));
        	
        	if(euclidianDistance(p1,closestPoint) <= euclidianDistance(p2, closestPoint)) {
        		currPoint[0] = temp_lat;
            	currPoint[1] = temp_long;
            	degreeTheta = finaldeg;
        	}
        	else {
        		currPoint[0] = temp_lat_2;
            	currPoint[1] = temp_long_2;
        	}
        	
        	
        	droneMoves = droneMoves + degreeTheta + ",";
        	System.out.println("Final direction of movement: " + degreeTheta);
        	dronePath.add(Point.fromLngLat(currPoint[1], currPoint[0]));
        	System.out.println("_________________________________________________");
        	droneMoves = droneMoves + currPoint[0] + "," + currPoint[1] + ",";
        	if(euclidianDistance(currPoint, closestPoint) < 0.0002) {
        		//if we are near the starting point (should be 0.0003 but ok).
        		if(closestPoint[0] == startingPoint.latitude() && closestPoint[1] == startingPoint.longitude()) {
            		break droneSearchAlgorithm;
        		}
        		sensorData.get(closestIndex).setIsRead(true);
        		System.out.println("Sensor loc of read: " + sensorData.get(closestIndex).getLocation());
        		droneMoves = droneMoves + sensorData.get(closestIndex).getLocation() + "\n";
        		//adding marker on the sensor that is being read
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
        }//end of while loop  
        
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
    
    private static boolean throughNoFlyZone(double[] point1, double direction, Map<String, Polygon> buildings) {
    	for(double i = 0.00001; i < 0.0003; i += 0.00001) {
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
    
    private static Coordinate WW3ToCoordinates(String threewords) throws IOException, InterruptedException {
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
    private static double euclidianDistance(double[] point1, double[] point2) {
    	final var distance = Math.pow((point1[0] - point2[0]),2) + Math.pow((point1[1] - point2[1]), 2);
    	return Math.sqrt(distance);
    }
    
    private static void writeToFile(FeatureCollection path, String date, String month, String year) {
    	try {
    		final File newFile = new File("readings-"+date+"-"+month+"-"+year+".geojson");
            var fileWrite = new FileWriter(newFile);
            fileWrite.write(path.toJson());
            fileWrite.close();
         } catch (IOException error) {
            error.printStackTrace();
       }
    }
    
    private static void writeToFile(String moves, String date, String month, String year) {
    	try {
    		final File newFile = new File("flightpath-"+date+"-"+month+"-"+year+".txt");
            FileWriter fileWrite = new FileWriter(newFile);
            fileWrite.write(moves);
            fileWrite.close();
         } catch (IOException error) {
            error.printStackTrace();
       }
    }
    
    private static int findClosestSensor(double[] currentLocation, ArrayList<SensorReadings> sensorData) {
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
    
//    private static boolean furtherNoFlyZone(double[] point1, double direction, Map<String, Polygon> buildings, Polygon map) {
//    	for(double i = 0.0003; i < 0.008; i += 0.0004) {
//    		var temp_lat = point1[0] + Math.sin(direction)*(i);
//        	var temp_long = point1[1] + Math.cos(direction)*(i);
//        	if(!(TurfJoins.inside(Point.fromLngLat(temp_long, temp_lat), map))){
//        		return true;
//        	}
//        	for(Polygon keys : buildings.values()){
//        		if(TurfJoins.inside(Point.fromLngLat(temp_long, temp_lat), keys)) {
//        			return true;
//        		}
//        	}
//        	
//    	}
//    	return false;
//    }
}