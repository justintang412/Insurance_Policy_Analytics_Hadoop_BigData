package com.ncl.spark

import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll
import java.sql.Date

class CleansingSparkJobTest extends AnyFunSuite with BeforeAndAfterAll {

  // Lazily initialize a SparkSession for testing
  @transient private var spark: SparkSession = _

  // Create a SparkSession before the tests start
  override def beforeAll(): Unit = {
    spark = SparkSession.builder()
      .appName("CleansingJobTest")
      .master("local[*]") // Use a local Spark instance for testing
      .config("spark.driver.host", "localhost") // Avoids network lookup issues
      .getOrCreate()
    super.beforeAll()
  }

  // Stop the SparkSession after the tests have finished
  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
    }
    super.afterAll()
  }

  // Test case for the policy data cleansing logic
  test("Cleansing logic should correctly transform policies data") {

    // Import the implicit encoders for creating DataFrames
    val sqlImplicits = spark.implicits
    import sqlImplicits._

    // 1. Create Sample Input Data: Mimic the raw data from Sqoop
    val rawPoliciesData = Seq(
      Row(101L, " active ", "Standard Coverage", "2020-01-15 10:00:00", "2021-01-14 10:00:00", 1200.50, "2020-01-15 00:00:00"),
      Row(102L, "PENDING", "N/A", "2021-03-20 12:00:00", "2022-03-19 12:00:00", 550.00, "2021-03-20 00:00:00"),
      Row(103L, "expired", "NULL", "2019-05-01 00:00:00", "2020-04-30 00:00:00", null, "2019-05-01 00:00:00"), // Null premium
      Row(104L, "ACTIVE", "", "2022-01-01 00:00:00", "2023-01-01 00:00:00", 2500.75, "2022-01-01 00:00:00") // Empty string coverage
    )

    val rawPoliciesSchema = StructType(Seq(
      StructField("policy_id", LongType, nullable = true),
      StructField("policy_status", StringType, nullable = true),
      StructField("coverage_details", StringType, nullable = true),
      StructField("effective_date", StringType, nullable = true),
      StructField("end_date", StringType, nullable = true),
      StructField("premium_amount", DoubleType, nullable = true),
      StructField("update_timestamp", StringType, nullable = true)
    ))

    val rawPoliciesDF = spark.createDataFrame(spark.sparkContext.parallelize(rawPoliciesData), rawPoliciesSchema)

    // 2. Apply the same cleansing logic from the main job
    val cleansedPolicies = rawPoliciesDF
      .withColumn("policy_status", F.trim(F.upper(F.col("policy_status"))))
      .withColumn("coverage_details", F.when(F.col("coverage_details").isin("N/A", "NULL", ""), null).otherwise(F.col("coverage_details")))
      .withColumn("effective_date", F.to_date(F.col("effective_date"), "yyyy-MM-dd HH:mm:ss"))
      .withColumn("end_date", F.to_date(F.col("end_date"), "yyyy-MM-dd HH:mm:ss"))
      .na.fill(0, Seq("premium_amount"))


    // 3. Define Expected Output Data
    val expectedData = Seq(
      Row(101L, "ACTIVE", "Standard Coverage", Date.valueOf("2020-01-15"), Date.valueOf("2021-01-14"), 1200.50),
      Row(102L, "PENDING", null, Date.valueOf("2021-03-20"), Date.valueOf("2022-03-19"), 550.00),
      Row(103L, "EXPIRED", null, Date.valueOf("2019-05-01"), Date.valueOf("2020-04-30"), 0.0), // Note the filled null
      Row(104L, "ACTIVE", null, Date.valueOf("2022-01-01"), Date.valueOf("2023-01-01"), 2500.75)
    )

    val expectedSchema = StructType(Seq(
        StructField("policy_id", LongType, nullable = true),
        StructField("policy_status", StringType, nullable = true),
        StructField("coverage_details", StringType, nullable = true),
        StructField("effective_date", DateType, nullable = true),
        StructField("end_date", DateType, nullable = true),
        StructField("premium_amount", DoubleType, nullable = false)
    ))
    
    val expectedDF = spark.createDataFrame(spark.sparkContext.parallelize(expectedData), expectedSchema)

    // 4. Assert that the cleansed DataFrame matches the expected DataFrame
    // We collect the data to the driver for comparison. This is safe for small test datasets.
    val cleansedResult = cleansedPolicies.select("policy_id", "policy_status", "coverage_details", "effective_date", "end_date", "premium_amount")
    
    // Check counts
    assert(cleansedResult.count() == expectedDF.count())

    // Check data equality by collecting and converting to a set
    assert(cleansedResult.collect().toSet == expectedDF.collect().toSet)
    
    println("Cleansing logic test passed successfully!")
  }
}
