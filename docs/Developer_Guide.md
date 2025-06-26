# **Developer Guide: Coding, Debugging, and Deploying the Pipeline**

Developer's workflow is from writing Spark code locally to deploying and monitoring the Oozie pipeline on the production Hadoop cluster.

-----

## 1\. Coding and Local Development Workflow

This phase focuses on writing and testing Spark jobs on local machine, which is faster and more efficient than testing directly on the cluster.

**Prerequisites:**

  * An IDE like IntelliJ IDEA (with the Scala plugin) or Visual Studio Code (with the Metals extension).
  * Java Development Kit (JDK) 8 or 11 installed locally.
  * Apache Maven installed locally.

**Step-by-Step Guide:**

1.  **Set Up the Project:**

      * Clone the `policy_analytics_platform` project repository.
      * Open the `spark-jobs/` directory as a new project in IDE. The IDE should automatically recognize it as a Maven project and download all the dependencies listed in `pom.xml`.

2.  **Write Spark Logic:**

      * Create new `.scala` files for jobs within the `src/main/scala/com/ncl/spark/` directory, following the existing structure.
      * Focus on the transformation logic. Use Spark's DataFrame API as shown in the examples.
      * For common, repeated logic (like adding audit columns), add a function to `JobUtils.scala` to keep code DRY (Don't Repeat Yourself).

3.  **Write Unit Tests:**

      * For any new job or significant change, create a corresponding test file in `src/test/scala/com/ncl/spark/`.
      * Follow the pattern in `CleansingSparkJobTest.scala`:
          * Create small, representative sample datasets (`Seq(...)`).
          * Apply transformation logic to the sample DataFrame.
          * Define the exact expected output.
          * Use `assert()` to compare the actual result with expected output.
      * **This is the most critical step for rapid development.** It allows you to verify logic in seconds without needing a cluster.

4.  **Run Tests Locally:**

      * Run tests directly from IDE (most have a "play" button next to the test class or individual test cases).
      * Alternatively, run all tests from the command line within the `spark-jobs/` directory:
        ```bash
        mvn clean test
        ```
      * If a test fails, use IDE's debugger to set breakpoints and step through Spark code to find the issue.

-----

## 2\. Deployment Workflow

Once code is working correctly locally and all tests are passing, you can deploy it to the Hadoop cluster.

**Step-by-Step Guide:**

1.  **Build the Application JAR:**

      * Navigate to the `spark-jobs/` directory in terminal.
      * Run the Maven package command. This compiles code, runs the tests again as a final check, and packages everything into a single JAR file.
        ```bash
        mvn clean package
        ```
      * The final artifact will be located at `spark-jobs/target/transformation-jobs-1.0.0-jar-with-dependencies.jar`.

2.  **Deploy the Oozie Application:**

      * Navigate to the `scripts/deployment/` directory.
      * Run the provided deployment script:
        ```bash
        ./deploy_oozie_app.sh
        ```
      * This script automates the following crucial steps:
          * It copies newly built Spark JAR into the Oozie application's `lib/` folder.
          * It uploads the entire Oozie application directory (`policy_pipeline/`), including the workflow definitions and the Spark JAR, to the correct location in HDFS (`/user/oozie/apps/policy_pipeline`).

3.  **Verify the Deployment in HDFS:**

      * You can double-check that the files were deployed correctly by listing the contents in HDFS:
        ```bash
        hdfs dfs -ls -R /user/oozie/apps/policy_pipeline
        ```
      * You should see `workflow.xml`, `coordinator.xml`, and the `lib/` directory containing `transformation-jobs.jar`.

-----

## 3\. Cluster Execution and Debugging

With the application deployed, you can now run the entire pipeline on the cluster and monitor it.

**Step-by-Step Guide:**

1.  **Submit and Run the Oozie Job:**

      * SSH into the master node (`master1.ncl.internal`).
      * Navigate to the deployed application directory on the local filesystem (where you store project, not in HDFS).
      * Submit the job using the `job.properties` file and the Oozie CLI:
        ```bash
        oozie job -config oozie-workflows/policy_pipeline/job.properties -run
        ```
      * This command will start the coordinator, which will then trigger the first run of the workflow.

2.  **Monitor the Job in the Oozie Web UI:**

      * Open a web browser and go to the Oozie UI: `http://master1.ncl.internal:11000/oozie`.
      * You will see `daily-policy-pipeline` coordinator and the running workflow instance.
      * The UI shows a visual graph of workflow. Actions turn green as they succeed, yellow while running, and red if they fail.

3.  **Debug Failed Jobs (The Critical Part):**

      * **If an Oozie action fails (turns red):**
          * Click on the failed action in the Oozie UI.
          * Click the "Error Log" or "Console URL" button. This is primary entry point for debugging.
      * **For a failed Spark job:** The "Console URL" will take you to the YARN Application Master page for that Spark job. This page is essential. Look for the "Logs" link.
      * **In the YARN Logs:** You will find logs for the **driver** and all **executors**.
          * Always check the `stderr` logs first. This is where Spark prints error messages and stack traces.
          * A common error is a `NullPointerException`, a file not found error, or an out-of-memory error. The stack trace will point you to the exact line in Scala code that caused the failure.
          * This process—**Oozie UI -\> YARN UI -\> Logs -\> stderr**—is the standard and most effective way to debug failed Spark jobs on a cluster.

4.  **Validate the Final Data:**

      * After the workflow succeeds, the final step is to ensure the data is correct.
      * SSH to the master node and launch the Hive client: `hive`.
      * Run queries against final tables to verify the output:
        ```sql
        -- Did the partitions get created for today's run?
        SHOW PARTITIONS fact_policy_transactions;

        -- Does the row count seem reasonable?
        SELECT processing_date, COUNT(*)
        FROM fact_policy_transactions
        GROUP BY processing_date
        ORDER BY processing_date DESC
        LIMIT 10;

        -- Spot-check a few records from the summary table
        SELECT * FROM agg_daily_policy_summary WHERE summary_date = current_date();
        ```