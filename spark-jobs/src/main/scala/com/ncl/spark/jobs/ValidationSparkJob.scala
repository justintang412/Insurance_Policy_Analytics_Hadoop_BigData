package com.ncl.spark.jobs

import org.apache.spark.sql.{SparkSession, functions => F}

/**
 * ValidationSparkJob: Second step.
 * - Reads cleansed data.
 * - Applies business validation rules.
 * - Separates valid records from invalid ones (quarantine).
 */
object ValidationSparkJob {
  def main(args: Array[String]): Unit = {
    if (args.length != 2) {
      System.err.println("Usage: ValidationSparkJob <cleansed-input-path> <validated-output-path>")
      System.exit(1)
    }

    val spark = SparkSession.builder().appName("PolicyData-ValidationJob").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    val cleansedBasePath = args(0)
    val validatedOutputPath = args(1)

    println(s"Starting Validation Job. Reading from: $cleansedBasePath")
    // This example focuses on policies; a real job might validate other entities too.
    val policiesDF = spark.read.parquet(s"$cleansedBasePath/policies")

    // --- Validation Logic ---
    val validatedPolicies = policiesDF
      .withColumn("validation_errors", F.array(
        F.when(F.col("premium_amount") < 0, "NegativePremium")
         .otherwise(null),
        F.when(F.col("effective_date") > F.col("end_date"), "InvalidDateSequence")
         .otherwise(null),
        F.when(F.col("policy_status").isNull, "NullPolicyStatus")
         .otherwise(null),
        F.when(F.col("policy_id").isNull, "NullPolicyId")
          .otherwise(null)
      ))
      .withColumn("validation_errors", F.expr("filter(validation_errors, x -> x is not null)"))
      .withColumn("is_valid", F.size(F.col("validation_errors")) === 0)

    val validRecords = validatedPolicies.filter(F.col("is_valid")).drop("validation_errors", "is_valid")
    val invalidRecords = validatedPolicies.filter(!F.col("is_valid")).drop("is_valid")
    
    val validCount = validRecords.count()
    val invalidCount = invalidRecords.count()
    println(s"Validation complete. Valid records: $validCount, Invalid records: $invalidCount")

    println(s"Writing valid records to: $validatedOutputPath/policies")
    validRecords.write.mode("overwrite").parquet(s"$validatedOutputPath/policies")

    if (invalidCount > 0) {
      println(s"Writing quarantined records to: $validatedOutputPath/policies_quarantined")
      invalidRecords.write.mode("overwrite").parquet(s"$validatedOutputPath/policies_quarantined")
    }

    println("Validation Job finished successfully.")
    spark.stop()
  }
}
