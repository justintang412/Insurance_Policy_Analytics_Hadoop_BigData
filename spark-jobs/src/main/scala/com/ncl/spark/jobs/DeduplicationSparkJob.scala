package com.ncl.spark.jobs

import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.{SparkSession, functions => F}

/**
 * DeduplicationSparkJob: Third step.
 * - Reads validated data.
 * - Uses window functions to find and remove duplicate records based on business keys.
 */
object DeduplicationSparkJob {
  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      System.err.println("Usage: DeduplicationSparkJob <validated-input-path> <deduped-output-path>")
      System.exit(1)
    }

    val spark = SparkSession.builder().appName("PolicyData-DeduplicationJob").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val validatedBasePath = args(0)
    val dedupedOutputPath = args(1)

    println(s"Starting Deduplication Job. Reading from: $validatedBasePath")
    val policiesDF = spark.read.parquet(s"$validatedBasePath/policies")
    
    // --- Deduplication Logic ---
    // For each policy_id, we want to keep the most recently updated record.
    // We assume an 'update_timestamp' column exists from the source system.
    val windowSpec = Window.partitionBy("policy_id").orderBy(F.col("update_timestamp").desc)

    val dedupedPolicies = policiesDF
      .withColumn("row_num", F.row_number().over(windowSpec))
      .filter(F.col("row_num") === 1)
      .drop("row_num")

    println(s"Writing deduplicated data to: $dedupedOutputPath/policies")
    dedupedPolicies.write.mode("overwrite").parquet(s"$dedupedOutputPath/policies")

    println("Deduplication Job finished successfully.")
    spark.stop()
  }
}