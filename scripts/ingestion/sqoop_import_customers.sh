import \
    --connect jdbc:oracle:thin:@<pas-oracle-db-host>:1521/<service_name> \
    --username <pas_user> \
    --password-file hdfs:///user/hadoop/secure/pas.password \
    --table CUSTOMERS \
    --target-dir /user/hive/warehouse/customers_raw \
    --as-parquetfile \
    --split-by customer_id \
    -m 8 \
    --hive-import \
    --hive-database default \
    --hive-table customers_raw \
    --hive-overwrite
