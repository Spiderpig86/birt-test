# Application Setup
* Instructions from the original repo are found in `README.old`.

## Application Dependecies
* Make sure that all dependencies are correctly loaded from Maven.
* If the application is facing lifecycle errors, try deleting `/.m2/` within your user folder, right click on the project, click `Run As`, and click `Maven Build`.

## Setting Up MySQL
1. Open `MySQL CLI` and run:
	```bash
	mysql -u root -p
	create database classicmodels;
	grant all privileges on classicmodels.* to birt@'127.0.0.1' identified by 'birt';
	grant all privileges on classicmodels.* to birt@'%' identified by 'birt' ;
	```
	* If you are getting issues accessing the database, try allowing access to root.
	```bash
	grant all privileges on classicmodels.* to root@'127.0.0.1' identified by 'root';
	grant all privileges on classicmodels.* to root@'%' identified by 'root' ;
	```
	
2. Create the db with `create_classicmodels.sql` located in `...\spring-birt-integration-example\src\main\resources\birt-database-2_0_1\ClassicModels\mysql` by running:
	```bash
	mysql -u birt -h127.0.0.1 -p birt classicmodels < create_classicmodels.sql
	```
	* NOTE: When you run this command the initial several statements at the head of the file are DROP TABLE commands. These commands should be removed as they will fail.
	
3. Load the data into the database with:
	```bash
	mysql classicmodels --local_infile=1 -uroot -p < load_classicmodels.sql
	```
	* If a single row only gets loaded, change the line endings of the query to be '\n' instead of '\r\n' or vice versa.
	```sql
	LOAD DATA LOCAL INFILE 'datafiles/customers.txt' INTO TABLE Customers
          FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\r\n';
	```

	
## Changes from Original
* Removed all non-ASCII chars from sample data files since they caused some issues with parsing.
* Set resource path so BIRT engine knows where to fetch styles and images.
* Method added in `BirtEngineFactory.java` to resolve resource path automatically.