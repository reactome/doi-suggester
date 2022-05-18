# doi-suggester
Suggests which entities might need a DOI

# Dependencies
- Maven2
- Java

# Installation
Afer cloning repo runn the following command from the root folder
```bash
mvn clean package
```

This will create the following jar file: target/doi-suggester-0.0.1-SNAPSHOT-jar-with-dependencies.jar
  
# Setup
Create config file: src/main/resources/config.properties
  
The file should have the following content
```
automatedDOIs.user=
automatedDOIs.password=
automatedDOIs.dbName=slice_test
automatedDOIs.prevDbName=slice_current
automatedDOIs.host=localhost
automatedDOIs.port=3306
```
  
# Running the DOI Suggester
 
```bash
  java -jar target/doi-suggester-0.0.1-SNAPSHOT-jar-with-dependencies.jar
```
