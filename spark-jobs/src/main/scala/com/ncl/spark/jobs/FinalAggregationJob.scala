package com.ncl.spark.jobs

import org.apache.spark.sql.{SparkSession, functions => F}

/**
 * FinalAggregationJob: Final step.
 * - Joins multiple clean datasets (policies, customers).
 * - Creates an aggregated summary table (data mart) for BI/analyst use.
 * - Writes the final detailed and aggregated tables to the data warehouse layer.
 */
object FinalAggregationJob {
  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      System.err.println("Usage: FinalAggregationJob <deduped-policies-path> <cleansed-customers-path> <final-output-path>")
      System.exit(1)
    }

    val spark = SparkSession.builder().appName("PolicyData-FinalAggregationJob").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val dedupedPoliciesPath = args(0)
    val cleansedCustomersPath = args(1)
    val finalOutputPath = args(2)

    println(s"Starting Final Aggregation Job. Reading from: $dedupedPoliciesPath and $cleansedCustomersPath")
    val policiesDF = spark.read.parquet(dedupedPoliciesPath)
    val customersDF = spark.read.parquet(cleansedCustomersPath)

    // --- Join policies with customers to create a final enriched dataset ---
    val enrichedPolicies = policiesDF.join(customersDF, "customer_id", "inner")
    val finalEnrichedPolicies = JobUtils.withAuditColumns(enrichedPolicies)

    println(s"Writing final enriched policy data to: $finalOutputPath/policies_final")
    // This table is the "single source of truth" for detailed policy records.
    finalEnrichedPolicies
      .write
      .partitionBy("processing_date")
      .mode("overwrite")
      .parquet(s"$finalOutputPath/policies_final")


    // --- Create an aggregated data mart for daily reporting ---
    val dailySummary = finalEnrichedPolicies
      .groupBy("processing_date", "policy_status")
      .agg(
        F.count("policy_id").alias("policy_count"),
        F.sum("premium_amount").alias("total_premium"),
        F.avg("premium_amount").alias("average_premium"),
        F.countDistinct("customer_id").alias("distinct_customer_count")
      )

    println(s"Writing daily summary data mart to: $finalOutputPath/daily_policy_summary")
    dailySummary
      .write
      .partitionBy("processing_date")
      .mode("overwrite")
      .parquet(s"$finalOutputPath/daily_policy_summary")

    println("Final Aggregation Job finished successfully.")
    spark.stop()
  }
}