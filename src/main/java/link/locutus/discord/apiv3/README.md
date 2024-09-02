# About this directory
 - This reuses the pool from previous API versions
 - All type classes are generated from the schema file (see resources directory)
 - The csv directory handles parsing of the PW nation and city data csv files
 - See the build.gradle for the task compiling the schema to java class
 - The main handler for queries is PoliticsAndWarV3
 - The RequestTracker handles rate limiting and request prioritization

PWAPIV3 is not used. Ignore it.