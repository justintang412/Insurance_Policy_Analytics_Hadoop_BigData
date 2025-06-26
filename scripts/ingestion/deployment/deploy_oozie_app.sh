#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Variables ---
SPARK_PROJECT_DIR="../../spark-jobs"
OOZIE_APP_DIR="../../oozie-workflows/policy_pipeline"
HDFS_OOZIE_APP_PATH="/user/oozie/apps/policy_pipeline"
SPARK_JAR_NAME="transformation-jobs-1.0.0-jar-with-dependencies.jar"

# --- Main Script Logic ---

echo "--- 1. Building Spark application JAR ---"
cd "$SPARK_PROJECT_DIR"
mvn clean package -DskipTests
echo "Spark JAR built successfully."
cd - # Return to the original directory

echo "--- 2. Preparing Oozie application directory ---"
# Create a temporary deployment directory
rm -rf deploy
mkdir -p deploy/policy_pipeline/lib

# Copy Oozie XML files and properties
cp "$OOZIE_APP_DIR"/*.xml deploy/policy_pipeline/
cp "$OOZIE_APP_DIR"/job.properties deploy/policy_pipeline/

# Copy the newly built Spark JAR to the 'lib' directory
cp "$SPARK_PROJECT_DIR/target/$SPARK_JAR_NAME" deploy/policy_pipeline/lib/transformation-jobs.jar

echo "Oozie application directory prepared."

echo "--- 3. Deploying application to HDFS ---"
# Remove the old version from HDFS if it exists
hdfs dfs -rm -r -skipTrash "$HDFS_OOZIE_APP_PATH" || true

# Copy the new application directory to HDFS
hdfs dfs -put deploy/policy_pipeline "$HDFS_OOZIE_APP_PATH"

echo "--- Deployment Complete ---"
echo "Oozie application successfully deployed to HDFS at: $HDFS_OOZIE_APP_PATH"

# Clean up the local deployment directory
rm -rf deploy