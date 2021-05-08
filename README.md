## [Informatics Large Practical](http://www.drps.ed.ac.uk/20-21/dpt/cxinfr09051.htm)

***

This coursework is related to the Informatics Large Practical. 

The coursework specification: 

The task was to build a simulator of a drone. The drone would be given sensor location data, and in turn had to map the best possible route to visit each sensor and return to the starting point. This course was completed in Java, using the MapBox SDK. 

Example of flight-path: 
![Image generated in geojson.io](https://github.com/parna86/ilp_cw2/blob/main/flight-path.png)

The above path was generated from a list of 33 sensors. The markers are given a colour based on the air pollution levels recorded by the sensor. The sensor data was read from a web server running locally. The output generated is stored in a .geojson file, which when uploaded on [geojson.io](http://geojson.io/#map=2/20.0/0.0), shows the above image. You can try this out yourself using the files in the outputs folder. 
