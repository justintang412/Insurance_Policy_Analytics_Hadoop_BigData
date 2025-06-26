### **Apache Oozie Installation and Workflow Configuration**

### **1. Introduction**

Apache Oozie served as the central orchestration and scheduling engine for the Policy Analytics Platform. It was described as the "brain of the pipeline", responsible for automating the entire daily ETL workflow. Oozie's primary role was to schedule and coordinate the sequence of dependent Sqoop and **Spark jobs**, ensuring that data was ingested and processed in the correct order every day.

This document outlines the installation of Apache Oozie on `master1.ncl.internal` and provides a representative example of the workflow definition used to manage the daily pipeline. This pipeline consisted of approximately 15-17 jobs in total: 5-7 for Sqoop ingestion and around 10 for **Spark transformation**.

### **2. Oozie Installation**

All the following steps are performed on `master1.ncl.internal` as the `hadoop` user.

**2.1. Download and Build Oozie**

1.  **Install Build Tools**: On `master1.ncl.internal`, install Apache Maven, which is required to build the Oozie package.
    ```bash
    sudo yum install -y apache-maven
    ```
2.  **Download and Unpack Oozie**: On `master1.ncl.internal`, download and unpack the Oozie source tarball into the hadoop user's home directory.
    ```bash
    # Location: master1.ncl.internal
    # Directory: /home/hadoop
    cd /home/hadoop
    wget https://archive.apache.org/dist/oozie/4.3.0/oozie-4.3.0.tar.gz
    tar -xzf oozie-4.3.0.tar.gz
    mv oozie-4.3.0 oozie-src # Rename to avoid confusion
    ```
3.  **Build Oozie**: From the source directory, run the build script. This will compile Oozie against the project's Hadoop version.
    ```bash
    # Location: master1.ncl.internal
    # Directory: /home/hadoop/oozie-src
    cd /home/hadoop/oozie-src
    ./bin/mkdistro.sh -DskipTests
    ```
    The final, installable package will be created at `/home/hadoop/oozie-src/distro/target/oozie-4.3.0-distro.tar.gz`.

**2.2. Oozie Setup**

1.  **Install Oozie**: Unpack the distributable package into the hadoop home directory. This will be the final Oozie installation directory.
    ```bash
    # Location: master1.ncl.internal
    # Directory: /home/hadoop
    cd /home/hadoop
    tar -xzf oozie-src/distro/target/oozie-4.3.0-distro.tar.gz
    # The final installation is now in /home/hadoop/oozie-4.3.0
    ```
2.  **Install ExtJS Library (for Web UI)**: The Oozie web console requires the ExtJS library.
    ```bash
    # Location: master1.ncl.internal
    # Directory: /home/hadoop
    cd /home/hadoop
    wget http://extjs.com/deploy/ext-2.2.zip

    # Unzip directly into the Oozie webapp directory
    unzip ext-2.2.zip -d /home/hadoop/oozie-4.3.0/hadooplibs/webapps/oozie/
    ```
3.  **Prepare Oozie `sharelib`**: Copy the Oozie share library, which contains dependencies for various jobs, into HDFS. This must be done before starting Oozie. **This step is critical for Spark jobs.**
    ```bash
    # Location: master1.ncl.internal
    # As hadoop user
    cd /home/hadoop/oozie-4.3.0
    tar -xzf oozie-sharelib-4.3.0.tar.gz

    # Add the Spark JARs to the Oozie sharelib
    # You MUST copy the Spark assembly jar into the new 'spark' directory
    cp /home/hadoop/spark/jars/spark-assembly*.jar share/lib/spark/

    # The sharelib is in a 'share' directory. Put it into HDFS under /user/oozie
    # This location is the default Oozie expects.
    hdfs dfs -put share /user/oozie/
    ```

### **3. Oozie Configuration**

**3.1. Database Setup for Oozie**

1.  **Create Oozie Database and User**: On `master1.ncl.internal`, log into MySQL as the root user and create a dedicated database and user for Oozie.
    ```sql
    CREATE DATABASE oozie;
    CREATE USER 'oozieuser'@'master1.ncl.internal' IDENTIFIED BY 'ooziepassword';
    GRANT ALL PRIVILEGES ON oozie.* TO 'oozieuser'@'master1.ncl.internal';
    FLUSH PRIVILEGES;
    ```
2.  **Configure `oozie-site.xml`**: On `master1.ncl.internal`, edit the Oozie configuration file to point to the MySQL database and the Hadoop configuration directory.
    ```bash
    # File to edit on master1.ncl.internal:
    # /home/hadoop/oozie-4.3.0/conf/oozie-site.xml
    vi /home/hadoop/oozie-4.3.0/conf/oozie-site.xml
    ```
    Add the following properties inside the `<configuration>` tags:
    ```xml
    <property>
        <name>oozie.service.JPAService.jdbc.driver</name>
        <value>com.mysql.jdbc.Driver</value>
    </property>
    <property>
        <name>oozie.service.JPAService.jdbc.url</name>
        <value>jdbc:mysql://master1.ncl.internal:3306/oozie</value>
    </property>
    <property>
        <name>oozie.service.JPAService.jdbc.username</name>
        <value>oozieuser</value>
    </property>
    <property>
        <name>oozie.service.JPAService.jdbc.password</name>
        <value>ooziepassword</value>
    </property>
    <property>
        <name>oozie.service.HadoopAccessorService.hadoop.configurations</name>
        <value>*=/home/hadoop/hadoop/etc/hadoop</value>
    </property>
    ```
3.  **Initialize Oozie Database Schema**: On `master1.ncl.internal`, copy the MySQL JDBC driver to the Oozie library and run the database creation tool.
    ```bash
    # Location: master1.ncl.internal
    # Directory: /home/hadoop/oozie-4.3.0
    cd /home/hadoop/oozie-4.3.0

    # Assuming mysql-connector-java.jar is in hadoop's home directory
    cp /home/hadoop/mysql-connector-java-8.0.28.jar libext/

    # Create the DB schema
    ./bin/ooziedb.sh create -sqlfile oozie.sql -run
    ```

### **4. Starting and Verifying Oozie**

1.  **Start the Oozie Server**: On `master1.ncl.internal`:
    ```bash
    /home/hadoop/oozie-4.3.0/bin/oozied.sh start
    ```
2.  **Verify Oozie Status**: From `master1.ncl.internal`, run the admin command line tool.
    ```bash
    /home/hadoop/oozie-4.3.0/bin/oozie admin -oozie http://master1.ncl.internal:11000/oozie -status
    # Expected output: System mode: NORMAL
    ```

### **5. Oozie Workflow**

This section describes the Oozie UI and the configuration files that define the daily ETL pipeline, coordinating all ingestion and transformation jobs.

**5.1. Oozie Web UI and Access**

Apache Oozie includes a web-based user interface for monitoring and managing jobs. For this project, it is accessible on the master node where the Oozie service is running.

  * **Access URL:** `http://master1.ncl.internal:11000/oozie`
  * **Key Features:**
      * **Job Monitoring:** View a list of all submitted jobs (Coordinators and Workflows) and their current status (`RUNNING`, `SUCCEEDED`, `FAILED`, etc.).
      * **Visual DAG:** For any workflow, the UI displays a visual graph of the job dependencies. This allows you to see the exact flow of execution, including which parallel actions have completed.
      * **Debugging and Log Access:** If an action fails, it is highlighted in red. You can click the failed action to get direct access to its error logs for troubleshooting.
      * **Job Control:** The UI provides basic control to suspend, resume, or kill running jobs. It also allows for rerunning failed jobs.

**5.2. Explanation of Workflow Configuration Files**

The Oozie pipeline is defined by a set of interconnected files stored in HDFS.

  * **`job.properties`**
    This file is the entry point for launching a pipeline. It's a key-value file that defines all variables and parameters used by the coordinator and workflow, keeping the core XML logic reusable. It is used to launch a job via the command: `oozie job -config job.properties -run`.
  * **`coordinator.xml`**
    This file is the **scheduler**. It defines *when* and *how often* the workflow should run, based on time and data availability. For this project, it defines a daily frequency and implements the **T+2 data latency** by creating a data dependency on HDFS paths from two days prior.
  * **`workflow.xml`**
    This file is the **execution plan** or the **Directed Acyclic Graph (DAG)**. It defines the sequence of actions and their dependencies. It uses `<fork>` and `<join>` nodes to run the **\~7 Sqoop ingestion jobs** in parallel, and then defines the sequence of the **\~10 Spark jobs** for transformation.

**5.3. Coordinator (`coordinator.xml`)**

```xml
<coordinator-app name="daily-policy-pipeline"
                 frequency="${coord:days(1)}"
                 start="${start_time}" end="${end_time}" timezone="UTC"
                 xmlns="uri:oozie:coordinator:0.2">
   <datasets>
      <dataset name="raw_policy_data" frequency="${coord:days(1)}"
               initial-instance="${start_time}" timezone="UTC">
         <uri-template>
            hdfs://master1.ncl.internal:9000/data/pas/raw/${coord:formatTime(coord:current(-2), 'yyyy/MM/dd')}
         </uri-template>
         <done-flag></done-flag>
      </dataset>
   </datasets>
   <input-events>
      <data-in name="input_data" dataset="raw_policy_data">
         <instance>${coord:current(0)}</instance>
      </data-in>
   </input-events>
   <action>
      <workflow>
         <app-path>${workflowAppPath}</app-path>
      </workflow>
   </action>
</coordinator-app>
```

**5.4. Workflow (`workflow.xml`)**

This workflow details the execution graph for all \~15-17 jobs, ingesting 6 data entities in parallel before running the sequential transformation jobs.

```xml
<workflow-app name="policy-etl-workflow" xmlns="uri:oozie:workflow:0.5">
    <start to="ingestion-fork"/>

    <fork name="ingestion-fork">
        <path start="sqoop-policies"/>
        <path start="sqoop-customers"/>
        <path start="sqoop-payments"/>
        <path start="sqoop-coverages"/>
        <path start="sqoop-schedules"/>
        <path start="sqoop-endorsements"/>
    </fork>

    <action name="sqoop-policies">
        <sqoop xmlns="uri:oozie:sqoop-action:0.2">
            <job-tracker>${jobTracker}</job-tracker>
            <name-node>${nameNode}</name-node>
            <command>import --connect jdbc:oracle... --table POLICIES --target-dir /user/hive/warehouse/policies_raw ...</command>
        </sqoop>
        <ok to="ingestion-join"/>
        <error to="kill"/>
    </action>

    <action name="sqoop-customers">
        <sqoop xmlns="uri:oozie:sqoop-action:0.2">
            <job-tracker>${jobTracker}</job-tracker>
            <name-node>${nameNode}</name-node>
            <command>import --connect jdbc:oracle... --table CUSTOMERS --target-dir /user/hive/warehouse/customers_raw ...</command>
        </sqoop>
        <ok to="ingestion-join"/>
        <error to="kill"/>
    </action>
    
    <action name="sqoop-payments">...</action>
    <action name="sqoop-coverages">...</action>
    <action name="sqoop-schedules">...</action>
    <action name="sqoop-endorsements">...</action>

    <join name="ingestion-join" to="spark-cleansing-job"/>

    <action name="spark-cleansing-job">
        <spark xmlns="uri:oozie:spark-action:0.1">
            <job-tracker>${jobTracker}</job-tracker>
            <name-node>${nameNode}</name-node>
            <master>yarn-cluster</master>
            <name>CleansingJob</name>
            <class>com.ncl.CleansingSparkJob</class>
            <jar>${workflowAppPath}/lib/transformation-jobs.jar</jar>
            <arg>/user/hive/warehouse/*_raw</arg>
            <arg>/user/hadoop/processed/cleansed</arg>
        </spark>
        <ok to="spark-validation-job"/>
        <error to="kill"/>
    </action>

    <action name="spark-validation-job">
        <spark xmlns="uri:oozie:spark-action:0.1">
            <job-tracker>${jobTracker}</job-tracker>
            <name-node>${nameNode}</name-node>
            <master>yarn-cluster</master>
            <name>ValidationJob</name>
            <class>com.ncl.ValidationSparkJob</class>
            <jar>${workflowAppPath}/lib/transformation-jobs.jar</jar>
            <arg>/user/hadoop/processed/cleansed</arg>
            <arg>/user/hadoop/processed/validated</arg>
        </spark>
        <ok to="spark-deduplication-job"/>
        <error to="kill"/>
    </action>
    
    <action name="spark-deduplication-job">
        <spark>...</spark>
        <ok to="spark-final-aggregation-job"/>
        <error to="kill"/>
    </action>

    <action name="spark-final-aggregation-job">
        <spark>...</spark>
        <ok to="end"/>
        <error to="kill"/>
    </action>

    <kill name="kill">
        <message>Workflow failed, error message[${wf:errorMessage(wf:lastErrorNode())}]</message>
    </kill>
    <end name="end"/>
</workflow-app>
```