# Apache Sqoop

### **1. Overview**

This document outlines the setup of the Data Ingestion Layer for the Policy Analytics Platform, a strategic project initiated in 2018 at a life insurance company. The primary goal of this layer is to perform a daily, automated bulk ingestion of policy data from the core transactional Oracle Policy Administration System (PAS) into the on-premise Hadoop cluster. This process makes the data available for large-scale transformation and analysis.

The two core components of this layer are:

  * **Apache Sqoop**: Used for the daily import of over 50 terabytes of raw policy data from the source relational database into HDFS.
  * **Apache Hive**: Used to provide a SQL-like interface for data access, creating a structured data warehouse on top of HDFS for over 50 concurrent analysts.

All setup and commands were executed on `master1.ncl.internal` as the `hadoop` user, unless otherwise specified.

### **2. Apache Sqoop: Data Ingestion from Oracle**

**Purpose**: Sqoop was responsible for connecting to the Oracle Policy Administration System (PAS) and ingesting the full historical record of over 30 million customers into the Hadoop Distributed File System (HDFS).

#### **2.1. Sqoop Installation**

1.  **Download and Extract**: Sqoop version 1.4.7 was downloaded and extracted to the `hadoop` user's home directory.
    ```bash
    # As hadoop user on master1.ncl.internal
    cd /home/hadoop
    wget https://archive.apache.org/dist/sqoop/1.4.7/sqoop-1.4.7.bin__hadoop-2.6.0.tar.gz
    tar -xzf sqoop-1.4.7.bin__hadoop-2.6.0.tar.gz
    mv sqoop-1.4.7.bin__hadoop-2.6.0 sqoop
    ```

#### **2.2. Sqoop Configuration**

1.  **Environment Variables**: The `SQOOP_HOME` environment variable was set in `/home/hadoop/.bashrc` to make Sqoop commands accessible from the path.

    ```bash
    # SQOOP ENVIRONMENT VARIABLES
    export SQOOP_HOME=/home/hadoop/sqoop
    export PATH=$PATH:$SQOOP_HOME/bin
    ```

2.  **Hadoop Dependency Configuration**: The `sqoop-env.sh` configuration file was updated to inform Sqoop where to find the Hadoop installation.

    ```bash
    # In $SQOOP_HOME/conf/sqoop-env.sh
    export HADOOP_HOME=/home/hadoop/hadoop
    export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
    export HADOOP_MAPRED_HOME=$HADOOP_HOME
    ```

3.  **Oracle JDBC Driver**: To enable Sqoop to communicate with the source Oracle database, the Oracle JDBC (OJDBC) driver was required. The driver was downloaded and copied into Sqoop's library directory.

    ```bash
    # Example steps to place the Oracle driver
    # (Assuming ojdbc8.jar was downloaded separately)
    cp ojdbc8.jar $SQOOP_HOME/lib/
    ```

#### **2.3. Sqoop Verification**

A version check was performed to confirm that the binaries were executable and that Sqoop could correctly identify its dependencies.

```bash
sqoop version
```

### **3. Apache Hive: Data Access and Warehouse**

**Purpose**: Hive was installed to provide a standard SQL interface for querying the vast amounts of policy data stored in HDFS. It served as the central data warehouse, organizing the raw ingested data into queryable tables and schemas.

#### **3.1. Hive Metastore: MySQL Setup**

For production stability and multi-user access, the Hive Metastore was configured to use an external MySQL database running on `master1.ncl.internal`. This metastore is critical as it stores all table definitions, schemas, and data locations.

1.  **Install MySQL**: The MySQL server was installed and started on `master1`.

    ```bash
    sudo yum install -y mysql-server
    sudo systemctl start mysqld
    sudo systemctl enable mysqld
    ```

2.  **Create Database and User**: A dedicated database named `metastore` and a user `hiveuser` were created in MySQL to manage Hive's metadata.

    ```sql
    CREATE DATABASE metastore;
    CREATE USER 'hiveuser'@'master1.ncl.internal' IDENTIFIED BY 'hivepassword';
    GRANT ALL PRIVILEGES ON metastore.* TO 'hiveuser'@'master1.ncl.internal';
    FLUSH PRIVILEGES;
    ```

#### **3.2. Hive Installation and Configuration**

1.  **Download and Extract**: Hive version 2.3.9 was downloaded and set up on `master1`.

    ```bash
    cd /home/hadoop
    wget https://archive.apache.org/dist/hive/hive-2.3.9/apache-hive-2.3.9-bin.tar.gz
    tar -xzf apache-hive-2.3.9-bin.tar.gz
    mv apache-hive-2.3.9-bin hive
    ```

2.  **Environment Variables**: The `HIVE_HOME` environment variable was added to `/home/hadoop/.bashrc`.

    ```bash
    # HIVE ENVIRONMENT VARIABLES
    export HIVE_HOME=/home/hadoop/hive
    export PATH=$PATH:$HIVE_HOME/bin
    ```

3.  **Update Sqoop Configuration**: With Hive installed, its location was added to `$SQOOP_HOME/conf/sqoop-env.sh` to enable direct integration between the tools.

    ```bash
    export HIVE_HOME=/home/hadoop/hive
    ```

4.  **Add MySQL JDBC Driver**: The MySQL Connector/J driver was copied to Hive's library directory, enabling it to communicate with its MySQL-based metastore.

    ```bash
    # Assuming the MySQL driver was already downloaded for Sqoop
    # A version like mysql-connector-java-8.0.28.jar was used
    cp mysql-connector-java-8.0.28/mysql-connector-java-8.0.28.jar $HIVE_HOME/lib/
    ```

5.  **Configure `hive-site.xml`**: This core configuration file was edited to connect Hive to the MySQL metastore and define its HDFS warehouse directory.

    ```xml
    <configuration>
      <property>
        <name>javax.jdo.option.ConnectionURL</name>
        <value>jdbc:mysql://master1.ncl.internal/metastore?createDatabaseIfNotExist=true</value>
      </property>
      <property>
        <name>javax.jdo.option.ConnectionDriverName</name>
        <value>com.mysql.cj.jdbc.Driver</value>
      </property>
      <property>
        <name>javax.jdo.option.ConnectionUserName</name>
        <value>hiveuser</value>
      </property>
      <property>
        <name>javax.jdo.option.ConnectionPassword</name>
        <value>hivepassword</value>
      </property>

      <property>
        <name>hive.metastore.warehouse.dir</name>
        <value>/user/hive/warehouse</value>
      </property>
      <property>
        <name>hive.exec.scratchdir</name>
        <value>/tmp/hive</value>
      </property>
    </configuration>
    ```

#### **3.3. Metastore Initialization and HDFS Setup**

1.  **Initialize Schema**: The `schematool` was run to create Hive's required metadata tables in the `metastore` MySQL database. This is a one-time setup step.

    ```bash
    $HIVE_HOME/bin/schematool -dbType mysql -initSchema
    ```

2.  **Create HDFS Directories**: The directories specified in `hive-site.xml` were created in HDFS to store warehouse data and temporary files.

    ```bash
    hdfs dfs -mkdir -p /user/hive/warehouse
    hdfs dfs -mkdir -p /tmp/hive
    hdfs dfs -chmod g+w /user/hive/warehouse
    hdfs dfs -chmod g+w /tmp/hive
    ```

#### **3.4. Hive Verification**

The installation was verified by launching the Hive command-line interface and running a simple command to ensure it could successfully connect to the metastore and communicate with HDFS.

```sql
hive> SHOW DATABASES;
-- Expected output:
-- OK
-- default
```

With these steps, the data ingest layer was fully configured, enabling the daily automated pipeline orchestrated by Apache Oozie to populate the data lake and empower enterprise-wide analytics.

### **4. Sqoop Data Import Jobs**

The daily data ingestion was executed by a Sqoop import command, which was scheduled and triggered by Apache Oozie. The command connected to the Oracle PAS, imported the policy data, and landed it in HDFS as Parquet files.

Below is a representative example of the Sqoop command used:

```bash
sqoop import \
    --connect jdbc:oracle:thin:@<pas-oracle-db-host>:1521/<service_name> \
    --username <pas_user> \
    --password-file hdfs:///user/hadoop/secure/pas.password \
    --query "SELECT policy_id, customer_id, coverage_details, premium_amount, effective_date FROM policies WHERE \$CONDITIONS" \
    --target-dir /user/hive/warehouse/policies_raw \
    --as-parquetfile \
    --split-by policy_id \
    -m 16 \
    --hive-import \
    --hive-database default \
    --hive-table policies_raw
```

For a single daily workflow there are more then 10 Sqoop ingestion jobs orchestrated by Apache Oozie covering the following data:

- Customer details

- Coverage information

- Premium schedules

- Payment histories

- Records of changes or endorsements

- The core policy data
