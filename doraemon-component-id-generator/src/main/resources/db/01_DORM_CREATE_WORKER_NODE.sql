DROP TABLE IF EXISTS DORM_WORKER_NODE;
CREATE TABLE DORM_WORKER_NODE
(
    ID                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    CREATED_BY         VARCHAR(32) NOT NULL COMMENT '创建人',
    CREATED_DATE       TIMESTAMP   NOT NULL COMMENT '创建时间',
    LAST_MODIFIED_BY   VARCHAR(32) NOT NULL COMMENT '最后更新人',
    LAST_MODIFIED_DATE TIMESTAMP   NOT NULL COMMENT '最后更新时间',
    HOST_NAME          VARCHAR(64) NOT NULL COMMENT '主机名称',
    PORT               VARCHAR(64) NOT NULL COMMENT '端口',
    TYPE               INT         NOT NULL COMMENT 'node type: ACTUAL or CONTAINER',
    LAUNCH_DATE        DATE        NOT NULL COMMENT 'launch date',
    PRIMARY KEY (ID)
) COMMENT ='服务器节点', ENGINE = INNODB;