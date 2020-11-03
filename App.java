package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;

import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.math.BigDecimal;

import com.google.gson.reflect.TypeToken;
import com.mapbox.geojson.FeatureCollection;



/**
 * So far, we can send a get request to the required url and get the air quality data as response. Now to create the required files.
 * The air quality data is stored in currReadings. Need to use the other command line arguments. 
 * 
 * Run WebServer: java -jar WebServerLite.jar
 * Run aqmaps: java -jar aqmaps-0.0.1-SNAPSHOT.jar 15 06 2021 55.9444 -3.1878 5678 80
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
    	
        HttpClient client = HttpClient.newHttpClient();
        
        /**
         * This is the request for the air quality data
         */
        HttpRequest requestAQ = HttpRequest.newBuilder()
              .uri(URI.create("http://localhost:" + args[6] + "/maps/" + args[2] + "/" + args[1] + "/" + args[0] + "/air-quality-data.json"))
              .header("Content-type", "application/json")
              .build();
        HttpResponse<String> responseAQ = client.send(requestAQ, BodyHandlers.ofString());
        
        double startingLat = Double.parseDouble(args[3]);
        double startingLong = Double.parseDouble(args[4]);
        
        /**
         * The request for the no-fly zones
         */
        HttpRequest requestBD = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + args[6] + "/buildings/no-fly-zones.geojson"))
                .header("Content-type", "application/json")
                .build();
        
        HttpResponse<String> responseBD = client.send(requestBD, BodyHandlers.ofString());
        
        String currReadings = responseAQ.body();
        String noFlyZones = responseBD.body();
//        System.out.println(currReadings);
//        System.out.println(noFlyZones);
        
        Type listType = new TypeToken<ArrayList<SensorReadings>>() {}.getType();
        ArrayList<SensorReadings> readings = new Gson().fromJson(currReadings, listType);
//        System.out.println(readings.get(0).location);
        for(SensorReadings one : readings) {
        	one.coordinates = convertCoord(one.location);
        }
        
        FeatureCollection buildings = FeatureCollection.fromJson(noFlyZones.toString());
        System.out.println(buildings.features().size());
        
//        System.out.println(readings.get(0).coordinates.lat + " " + readings.get(0).location);
        
        //now we have all the sensor readings.
        // now for the noFlyZones
        /*trial of convert Coord function - converts ww3 format -> coordinates*/
//        String sample = "slips.mass.baking";
//        convertCoord(sample);
        
    }
    
    public static Coordinate convertCoord(String threewords) throws IOException, InterruptedException {
    	HttpClient client = HttpClient.newHttpClient();
    	
    	String words[] = threewords.split("\\.");
    	HttpRequest requestCoord = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:80/words/" + words[0] + "/" + words[1]+ "/" + words[2] + "/details.json"))
                .header("Content-type", "application/json")
                .build();
    	
    	HttpResponse<String> coord = client.send(requestCoord, BodyHandlers.ofString());
    	
    	var sensor1 = new Gson().fromJson(coord.body().toString(), ww3ToCoord.class);
    	
    	return sensor1.coordinates;
    }
}

