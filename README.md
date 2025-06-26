# Big Data Project: The Policy Analytics Platform

## 1\. Project Overview

This project, the **Policy Analytics Platform**, was a strategic initiative to build a centralized big data solution for a life insurance company. Executed in 2018, the platform was engineered to ingest, process, and analyze massive volumes of policy data from a core transactional system.

The primary goal was to create a "single source of truth" for over 30 million customer policy records, transforming raw, siloed data into a reliable corporate asset. This foundational platform unlocked critical business intelligence capabilities, enabled advanced data science, and established a reference architecture for future data projects within the organization.

  * **Domain:** Insurance Policy & Enrollment Analytics
  * **Data Scale:** \~30 million historical records, 50+ TB of transactional history

## 2\. The Business Problem

Prior to this platform, the company faced significant challenges that hindered data-driven decision-making:

  * **Slow and Inefficient Reporting:** Generating key business reports was a manual, time-consuming process that took weeks and yielded inconsistent results due to queries running against live database replicas.
  * **Data Silos and Mistrust:** Policy data was fragmented across operational systems, leading to conflicting metrics and a lack of confidence in the data.
  * **Inability to Innovate:** The absence of a clean, centralized dataset blocked the data science team from developing crucial models for churn prediction or customer lifetime value.
  * **Operational Risk:** Running complex analytical queries directly on database replicas posed a significant performance risk to the customer-facing transactional systems.

## 3\. My Key Responsibilities & Contributions

As a key member of the development team, I had the following responsibilities:

  * Led the design and implementation of the data ingestion pipeline using **Apache Sqoop** to perform daily bulk imports from the source Oracle database.
  * Engineered the core data processing logic, developing a series of approximately 10 **Apache Spark** jobs to cleanse, validate, transform, and aggregate over 30 million policy records.
  * Played a lead role in the infrastructure team, contributing to the architecture, setup, and maintenance of the **100-VM on-premise Hadoop cluster**.
  * Collaborated with business stakeholders to define the data model for the analytical warehouse and establish critical data quality rules.

## 4\. Technical Architecture

The platform was built on a 100-node on-premise CentOS cluster using a standard Hadoop ecosystem stack.

### Core Components:

  * **Infrastructure:** On-premise cluster of \~100 VMs running CentOS 7.5.
  * **Data Ingestion:** **Apache Sqoop** for daily bulk imports from the Oracle Policy Administration System (PAS).
  * **Data Storage:** **Hadoop Distributed File System (HDFS)** as the primary data lake, with Transparent Data Encryption (TDE) enabled.
  * **Data Processing:** **Apache Spark** for all large-scale batch processing and data transformation logic, running on YARN.
  * **Data Access:** **Apache Hive** to provide analysts with a familiar SQL-like interface to query the processed data in the data warehouse.
  * **Orchestration & Scheduling:** **Apache Oozie** to automate the entire daily ETL workflow, managing dependencies between the Sqoop and Spark jobs.
  * **Cluster Management:** **Apache Ambari** for monitoring cluster health, resource utilization, and job performance.
  * **Security & Governance:** **Apache Ranger** for centralized, role-based access control and **Apache Atlas** for data cataloging and lineage.

## 5\. Key Challenges and Solutions

  * **Challenge: Infrastructure Complexity**
      * **Problem:** Building and configuring a 100-VM Hadoop cluster from scratch involved significant hardware, networking, and security hurdles.
      * **Solution:** We successfully provisioned the environment and implemented robust security, creating a stable and secure foundation for all data operations.
  * **Challenge: Data Quality and Consistency**
      * **Problem:** Data from legacy systems was plagued with inconsistencies, formatting errors, and missing values that broke downstream analytics.
      * **Solution:** I designed and implemented a multi-stage data validation and cleansing pipeline within our Spark jobs, which transformed the raw data into a trusted and reliable dataset.
  * **Challenge: Processing Performance**
      * **Problem:** A critical daily policy aggregation job initially took over 10 hours to run, failing to meet the business SLA.
      * **Solution:** I optimized the Spark job by implementing data partitioning strategies and tuning shuffle operations, which reduced the total execution time by over 60% to under 4 hours.

## 6\. Outcomes and Impact

The platform delivered significant, measurable value to the business:

  * **Accelerated Insights:** Reduced the time to generate key quarterly enrollment reports from **over one week to under four hours**.
  * **Enabled Advanced Analytics:** Unlocked the ability for the data science team to build the company's **first-ever policy lapse prediction model**, leading to new customer retention strategies.
  * **Established a Single Source of Truth:** The platform was adopted by over 50 analysts across 5 departments as the certified source for all policy data, eliminating reporting discrepancies.
  * **Set an Organizational Standard:** The platform's ingestion and data quality framework was formalized as a **reference architecture**, saving an estimated **4 months of development time** on the subsequent Claims Analytics Platform project.

## 7\. Project Documentation

For a more detailed look into the project's technical implementation, please see the following documents:

  * [**Requirement.md**](https://www.google.com/search?q=Requirement.md): Details the business needs, project scope, and key objectives.
  * [**Hadoop\_Cluster.md**](https://www.google.com/search?q=Hadoop_Cluster.md): A step-by-step guide on setting up the 100-node on-premise Hadoop cluster.
  * [**Apache\_Sqoop.md**](https://www.google.com/search?q=Apache_Sqoop.md): Outlines the setup of the data ingestion layer using Sqoop and the initial Hive data warehouse structure.
  * [**Spark\_Hive\_HBase.md**](https://www.google.com/search?q=Spark_Hive_HBase.md): Details the installation of core processing and access components like Spark, Hive, and HBase.
  * [**Apache\_Oozie.md**](https://www.google.com/search?q=Apache_Oozie.md): Describes the Oozie setup and the workflow files used to automate the daily ETL pipeline.
  * [**Developer\_Guide.md**](https://www.google.com/search?q=Developer_Guide.md): A comprehensive guide on the end-to-end developer workflow, from local coding to cluster deployment and debugging.