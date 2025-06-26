package com.ncl.spark.jobs

import org.apache.spark.sql.{SparkSession, functions => F}

/**
 * CleansingSparkJob: First step in the transformation pipeline.
 * - Reads multiple raw data sources.
 * - Applies cleaning rules: trimming strings, standardizing nulls, correcting data types.
 * - Writes cleansed data back to HDFS.
 */
object CleansingSparkJob {
  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      System.err.println("Usage: CleansingSparkJob <raw-input-path> <cleansed-output-path>")
      System.exit(1)
    }

    val spark = SparkSession.builder()
      .appName("PolicyData-CleansingJob")
      .enableHiveSupport()
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    val rawBasePath = args(0)
    val cleansedOutputPath = args(1)

    println(s"Starting Cleansing Job. Reading from: $rawBasePath")

    val rawDataFrames = JobUtils.readRawData(spark, rawBasePath)

    // --- Cleansing Policies ---
    val policiesDF = rawDataFrames("policies_raw")
    val cleansedPolicies = policiesDF
      .withColumn("policy_status", F.trim(F.upper(F.col("policy_status"))))
      .withColumn("coverage_details", F.when(F.col("coverage_details").isin("N/A", "NULL", ""), null).otherwise(F.col("coverage_details")))
      .withColumn("effective_date", F.to_date(F.col("effective_date"), "yyyy-MM-dd HH:mm:ss"))
      .withColumn("end_date", F.to_date(F.col("end_date"), "yyyy-MM-dd HH:mm:ss"))
      .na.fill(0, Seq("premium_amount"))

    // --- Cleansing Customers ---
    val customersDF = rawDataFrames("customers_raw")
    val cleansedCustomers = customersDF
      .withColumn("full_name", F.trim(F.initcap(F.col("full_name"))))
      .withColumn("gender", F.when(F.col("gender").isin("M", "Male"), "MALE")
                              .when(F.col("gender").isin("F", "Female"), "FEMALE")
                              .otherwise("UNKNOWN"))
      .withColumn("email", F.lower(F.trim(F.col("email"))))
      
    println(s"Writing cleansed data to: $cleansedOutputPath")
    cleansedPolicies.write.mode("overwrite").parquet(s"$cleansedOutputPath/policies")
    cleansedCustomers.write.mode("overwrite").parquet(s"$cleansedOutputPath/customers")
    // ... logic to write other cleansed DataFrames (payments, coverages) would go here ...

    println("Cleansing Job finished successfully.")
    spark.stop()
  }
}