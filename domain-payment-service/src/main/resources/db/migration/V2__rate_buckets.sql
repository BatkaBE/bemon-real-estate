CREATE TABLE request_buckets(bucket_key VARCHAR(180) PRIMARY KEY,window_start TIMESTAMPTZ NOT NULL,request_count INTEGER NOT NULL);
