package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import com.google.gson.Gson;

import java.lang.reflect.Type;

import com.google.gson.reflect.TypeToken;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;
import com.mapbox.turf.TurfMeasurement;

/**
 * So far, we can send a get request to the required url and get the air quality data as response. Now to create the required files.
 * The air quality data is stored in currReadings. Need to use the other command line arguments. 
 * 
 * Run WebServer: java -jar WebServerLite.jar
 * Run aqmaps: java -jar aqmaps-0.0.1-SNAPSHOT.jar 15 06 2021 55.9444 -3.1878 5678 80
 * Turf documentation : https://docs.mapbox.com/android/java/overview/turf/#working-with-geojson
 * 						https://docs.mapbox.com/archive/android/java/api/libjava-turf/4.0.0/com/mapbox/turf/TurfJoins.html
 * 
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
    	
    	String AQuri = "http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json";
    	String BDuri = "http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson";
        
    	String currReadings = sendHTTP(AQuri);
    	String noFlyZones = sendHTTP(BDuri);
    	
    	
        double startingLat = Double.parseDouble(args[3]);
        double startingLong = Double.parseDouble(args[4]);
        Point start = Point.fromLngLat(startingLong, startingLat);
        
        
        //this is for finding the actual coordinates for each of the ww3 strings - storing data in SensorReadings object only
        Type listType = new TypeToken<ArrayList<SensorReadings>>() {}.getType();
        ArrayList<SensorReadings> readings = new Gson().fromJson(currReadings, listType);
        for(SensorReadings one : readings) {
        	one.coordinates = convertCoord(one.location);
        }
        
        //below - extracting each of the coordinates for the buildings in a list of list of points.
        
        FeatureCollection buildings = FeatureCollection.fromJson(noFlyZones.toString());
        
        Feature f = Feature.fromGeometry(buildings.features().get(0).geometry());
        Polygon sample = (Polygon) f.geometry();
        
//        List<List<Point>> points = new ArrayList<List<Point>>();
//        for(Feature one : buildings.features()) {
//        	Polygon sample = (Polygon) one.geometry();
//        	points.add(sample.coordinates().get(0));
//        }
        
        System.out.println(TurfJoins.inside(start, sample));
        
    }
    
    /***
     * 
     * @param String threewords : represents the point's ww3 location.
     * @return Coordinate object with latitude and longitude corresponding to ww3
     * @throws IOException
     * @throws InterruptedException
     */
    public static Coordinate convertCoord(String threewords) throws IOException, InterruptedException {
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
    public static String sendHTTP(String uri) throws IOException, InterruptedException {
    	HttpClient client = HttpClient.newHttpClient();
    	HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-type", "application/json")
                .build();
          HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
          return response.body();
    }
}

