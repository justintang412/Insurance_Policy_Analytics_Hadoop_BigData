# Installing Spark, HBase, and Hive on a Hadoop Cluster

This is to install and configure Apache Spark, Apache HBase, and Apache Hive on the existing Hadoop cluster.

-----

## Apache Spark Installation

### Introduction to Apache Spark

Apache Spark is a high-speed, general-purpose cluster computing system. It offers a significant performance boost over traditional MapReduce by utilizing in-memory caching and optimized query execution, making it ideal for iterative algorithms, machine learning, and interactive data analysis. Spark provides rich APIs in Java, Scala, Python, and R, and can run on various cluster managers, including Hadoop YARN. By running on YARN, Spark can share the same cluster resources as the rest of the Hadoop ecosystem, accessing data directly from HDFS.

### Installation and Configuration (Run on `master1`)

These steps should be performed as the `hadoop` user on `master1`. Spark does not need to be installed on all worker nodes; YARN will handle distributing the Spark application components to the nodes at runtime.

1.  **Download and Extract Spark**

    Choose a Spark release that is pre-built for the version of Hadoop you are using (e.g., Hadoop 2.7).

    ```bash
    su - hadoop
    wget https://archive.apache.org/dist/spark/spark-2.4.8/spark-2.4.8-bin-hadoop2.7.tgz
    tar -xzf spark-2.4.8-bin-hadoop2.7.tgz
    mv spark-2.4.8-bin-hadoop2.7 spark
    ```

2.  **Set Environment Variables**

    Edit the `/home/hadoop/.bashrc` file and add the Spark environment variables.

    ```bash
    # Edit /home/hadoop/.bashrc
    vi ~/.bashrc
    ```

    Add the following lines:

    ```bash
    # SPARK ENVIRONMENT VARIABLES
    export SPARK_HOME=/home/hadoop/spark
    export PATH=$PATH:$SPARK_HOME/bin
    ```

    Source the `.bashrc` file to apply the new settings.

    ```bash
    source ~/.bashrc
    ```

3.  **Configure Spark Defaults**

    Navigate to the Spark configuration directory and create a `spark-defaults.conf` file from the template.

    ```bash
    cd $SPARK_HOME/conf
    cp spark-defaults.conf.template spark-defaults.conf
    ```

    Add the following lines to `spark-defaults.conf` to configure Spark to run on YARN. This makes YARN the default cluster manager, removing the need to specify it with every `spark-submit` command.

    ```properties
    spark.master yarn
    ```

4.  **Verification**

    Run a simple Spark Pi estimation example to verify the installation. The job will run across the YARN cluster.

    ```bash
    spark-submit --class org.apache.spark.examples.SparkPi \
    --master yarn \
    --deploy-mode cluster \
    --driver-memory 1g \
    --executor-memory 1g \
    --executor-cores 1 \
    $SPARK_HOME/examples/jars/spark-examples*.jar 10
    ```

    You can monitor the job's execution via the YARN ResourceManager web UI at `http://master1.ncl.internal:8088`.

-----

## Apache HBase Installation

### Introduction to Apache HBase

Apache HBase is an open-source, NoSQL, distributed database that runs on top of the Hadoop Distributed File System (HDFS). It is designed to provide random, strictly consistent, real-time access to petabytes of data. Unlike traditional relational databases, HBase is column-oriented and well-suited for storing sparse datasets, making it a powerful tool for large-scale, real-time applications.

### Installation and Configuration (Run on ALL Nodes)

HBase should be installed on all nodes in the cluster where you intend to run HBase services (Master or RegionServers). For this setup, we will install it on all masters and a subset of workers.

1.  **Download and Extract HBase (Run on ALL Nodes)**

    Perform these steps as the `hadoop` user.

    ```bash
    su - hadoop
    wget https://archive.apache.org/dist/hbase/1.4.13/hbase-1.4.13-bin.tar.gz
    tar -xzf hbase-1.4.13-bin.tar.gz
    mv hbase-1.4.13 hbase
    ```

2.  **Set Environment Variables (Run on ALL Nodes)**

    Edit the `/home/hadoop/.bashrc` file on all nodes where HBase is installed.

    ```bash
    # Edit /home/hadoop/.bashrc
    vi ~/.bashrc
    ```

    Add the following lines:

    ```bash
    # HBASE ENVIRONMENT VARIABLES
    export HBASE_HOME=/home/hadoop/hbase
    export PATH=$PATH:$HBASE_HOME/bin
    ```

    Source the file to apply the changes: `source ~/.bashrc`.

3.  **Configure HBase (Run on ALL Nodes)**

    Navigate to the `$HBASE_HOME/conf` directory.

    a. **`hbase-env.sh`**: Set the `JAVA_HOME` and configure HBase to use the YARN-managed ZooKeeper.

    ```bash
    # Edit hbase-env.sh
    export JAVA_HOME=$(readlink -f /usr/bin/java | sed "s:bin/java::")
    export HBASE_MANAGES_ZK=false
    ```

    b. **`hbase-site.xml`**: This is the main HBase configuration file. It needs to be configured to point to the HDFS NameNode and the ZooKeeper quorum.

    ```xml
    <configuration>
      <property>
        <name>hbase.rootdir</name>
        <value>hdfs://master1.ncl.internal:9000/hbase</value>
      </property>
      <property>
        <name>hbase.cluster.distributed</name>
        <value>true</value>
      </property>
      <property>
        <name>hbase.zookeeper.quorum</name>
        <value>master1.ncl.internal,master2.ncl.internal,worker01.ncl.internal</value>
      </property>
      <property>
        <name>hbase.zookeeper.property.dataDir</name>
        <value>/home/hadoop/zookeeper</value>
      </property>
    </configuration>
    ```

    c. **`regionservers` file**: This file specifies the nodes that will act as RegionServers, which are responsible for storing and serving the data. Edit this file on `master1`.

    ```
    worker01.ncl.internal
    worker02.ncl.internal
    ...
    worker97.ncl.internal
    ```

4.  **Starting and Verifying HBase (Run from `master1`)**

    a. **Start HBase**: Use the provided script to start the HBase cluster. This will start the HMaster on `master1` and RegionServers on the nodes listed in the `regionservers` file.

    ```bash
    start-hbase.sh
    ```

    b. **Enter HBase Shell**: Connect to the cluster using the HBase shell.

    ```bash
    hbase shell
    ```

    c. **Check Status**: Once in the shell, use the `status` command to check the health of the cluster.

    ```hbase
    hbase(main):001:0> status
    ```

    This should show 1 active master and the number of live region servers you configured.

-----

## Apache Hive Installation

### Introduction to Apache Hive

Apache Hive is a distributed, fault-tolerant data warehouse system that runs on top of Hadoop. It provides an SQL-like interface called HiveQL to enable users to read, write, and manage petabytes of data. Hive translates these SQL queries into MapReduce or Tez jobs that run on YARN, allowing non-programmers to perform large-scale data analysis without writing complex Java code. A central component of Hive is the Metastore, which stores the database and table metadata.

### Installation and Configuration (Run on `master1`)

Hive is primarily a client-side tool, but for a production setup, we will configure the Hive Metastore and HiveServer2 on `master1`.

1.  **Download and Extract Hive**

    Perform these steps as the `hadoop` user on `master1`.

    ```bash
    su - hadoop
    wget https://archive.apache.org/dist/hive/hive-2.3.9/apache-hive-2.3.9-bin.tar.gz
    tar -xzf apache-hive-2.3.9-bin.tar.gz
    mv apache-hive-2.3.9-bin hive
    ```

2.  **Set Environment Variables**

    Edit `/home/hadoop/.bashrc` on `master1`.

    ```bash
    # Edit /home/hadoop/.bashrc
    vi ~/.bashrc
    ```

    Add the following lines:

    ```bash
    # HIVE ENVIRONMENT VARIABLES
    export HIVE_HOME=/home/hadoop/hive
    export PATH=$PATH:$HIVE_HOME/bin
    ```

    Source the file: `source ~/.bashrc`.

3.  **Configure Hive**

    Navigate to the `$HIVE_HOME/conf` directory.

    a. **Create `hive-site.xml`**: Copy the template to create the configuration file.

    ```bash
    cp hive-default.xml.template hive-site.xml
    ```

    b. **Edit `hive-site.xml`**: Add the following properties. For a robust setup, it is highly recommended to configure a standalone database like MySQL for the metastore. The following configuration uses the default embedded Derby database, which is suitable only for single-user testing.

    ```xml
    <configuration>
      <property>
        <name>javax.jdo.option.ConnectionURL</name>
        <value>jdbc:derby:;databaseName=metastore_db;create=true</value>
        <description>JDBC connect string for a JDBC metastore</description>
      </property>
      <property>
        <name>hive.metastore.warehouse.dir</name>
        <value>/user/hive/warehouse</value>
        <description>location of default database for the warehouse</description>
      </property>
    </configuration>
    ```

4.  **Create HDFS Directories**

    Before starting Hive, you must create the necessary directories in HDFS and set the correct permissions.

    ```bash
    hdfs dfs -mkdir -p /user/hive/warehouse
    hdfs dfs -mkdir /tmp
    hdfs dfs -chmod g+w /user/hive/warehouse
    hdfs dfs -chmod g+w /tmp
    ```

5.  **Initialize Metastore Schema (Derby)**

    This is a one-time step that initializes the schema for the metastore database.

    ```bash
    cd $HIVE_HOME/bin
    ./schematool -dbType derby -initSchema
    ```

6.  **Verification**

    Start the Hive interactive shell to test the installation.

    ```bash
    hive
    ```

    Inside the Hive prompt, run a simple command to verify functionality:

    ```sql
    hive> SHOW DATABASES;
    OK
    default
    Time taken: ...
    ```