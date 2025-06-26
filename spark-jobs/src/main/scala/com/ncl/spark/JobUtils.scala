package com.ncl.spark

import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}

/**
 * A utility object to hold common functions and constants used across multiple Spark jobs.
 */
object JobUtils {

  /**
   * Reads multiple raw data sources (ingested by Sqoop) into DataFrames.
   * @param spark The active SparkSession.
   * @param rawBasePath The base HDFS path where raw tables are stored (e.g., /user/hive/warehouse).
   * @return A map of table names to their corresponding DataFrames.
   */
  def readRawData(spark: SparkSession, rawBasePath: String): Map[String, DataFrame] = {
    val tables = Seq("policies_raw", "customers_raw", "payments_raw", "coverages_raw")
    tables.map(table => (table, spark.read.parquet(s"$rawBasePath/$table"))).toMap
  }

  /**
   * Adds standardized audit columns to a DataFrame.
   * @param df The DataFrame to which columns will be added.
   * @return A new DataFrame with added audit columns.
   */
  def withAuditColumns(df: DataFrame): DataFrame = {
    df.withColumn("processed_timestamp", F.current_timestamp())
      .withColumn("processing_date", F.to_date(F.current_timestamp()))
  }
}