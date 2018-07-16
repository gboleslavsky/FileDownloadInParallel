# FileDownloadInParallel
Scala interview problem:

Given a .csv files with URLs that point to images on the Web, build an application that would:

Download these images in parallel and save it to the local filesystem at any predefined path. 
Please ensure that we don't have more than 10 downloads running in parallel. Add proper error 
handling, retries etc.

For each file downloaded store the following metrics:
a. Time taken to download 
b. File size

After all downloads are complete output the results from step 2 to console
Please build out this using async, non-blocking technologies in Scala using open source tech. 
Please provide a zip of the project folder with the build file and how to run it.


To run:
 unzip, 
 cd to folderUnzippedTo/src/main/scala  
 sbt compile 
 sbt run

Downloaded files will be in folderUnzippedTo/src/main/resources/downloaded-files

