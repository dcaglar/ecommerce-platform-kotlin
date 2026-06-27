So w have 2 databvase one central-db one is edge-db, and for each tadtabase we will perform 2 step operatoioon

edge-db scenario
step 1- Db initialation with role craetions,username passwrod crendetials are created on db using the credentials defined in  edge-db-init-secret.(Please 
note that, when usrs are fcreated, db's tables are not created yet, so make sure to apply certain sql queries to grant our users already accesss to new tables as well)
step 2 after centedials are created and db is up then a light weight liquibase container runs the cjangelog files defined in edge-db-changelog-configmap.xml.



edge-db
EDGE_DB_PAYMENT_SERVICE_USERNAME: ENC[AES256_GCM,data:B5T2k642GQeQLS92kPr+m+qp6E4SNz+ZOLzTCIpbBjM=,iv:TVdj434PhBIirjVLOyQP5HQzaT64uUFw0zK0KHdgf9c=,tag:ff/fEQxv28LDYiX6QVi6tw==,type:str]
EDGE_DB_PAYMENT_SERVICE_PASSWORD: ENC[AES256_GCM,data:OI4VtlxnFsVvlfJ6D1gcs9t34ymnFEyKESsOJRf9mfQ=,iv:H8X7QrMZppYs7dLZzucrJYShnHwzFgI4lUVZvcx9wxc=,tag:txUDUTJazwypS+N0eOaDqQ==,type:str]
EDGE_DB_PAYMENT_EDGE_WORKERS_USERNAME: ENC[AES256_GCM,data:esHZUJnk5BOwSjYuX17a4x/jvxV4/Q23xFy7HNjkdAmHJlTbwA==,iv:LVycPToYfk2pNb8JED9Ktm+cTRgArxd5Em3tuXvdiZM=,tag:XLZPZYetbugQ+bxK4jT87Q==,type:str]
EDGE_DB_PAYMENT_EDGE_WORKERS_PASSWORD: ENC[AES256_GCM,data:vXQ/9PiHWvMrIbgPNG615FslAnqg+9RImH+S6+++D0t4HhKR/Q==,iv:gL86lp+lgPtL26hotRH1Izu7Hbey6eCpCdP1kPwnPhI=,tag:WJxcuTP0WCQjgHIIar+VUQ==,type:str]
EDGE_DB_DUTY_USERNAME: ENC[AES256_GCM,data:BqPgFji8HtSIAizmw+NVoJ9bfsZD,iv:3af+LVwtZYPOXPNPHYEDiHOnR/n8CHcm+EGvkScWBIc=,tag:JDBNCrEV8J/TEpDDYZ/WPw==,type:str]
EDGE_DB_DUTY_PASSWORD: ENC[AES256_GCM,data:n2Xd8ErTOrbVZ+DyjCLj3wKQWfVN,iv:zJ5rqGKaB8Am1V1oKPK2CABM4xoUABKI55GAd0x2NZw=,tag:OsbAYdnW6img5feF3QQ0jA==,type:str]
EDGE_DB_POSTGRES_PASSWORD: ENC[AES256_GCM,data:QDBM6mUrlzzN62VXNcR0hmbygAH6Ju6qBw==,iv:MtWgON6+HIFe1wX82idpf5rk9QsBJXYhW13jp2hlEdc=,tag:NA1d1F3MtadUCIzMYaKBBg==,type:str]
EDGE_DB_MAINTENANCE_USERNAME -> A an applciationb or web service responsible for mainteneace task like partitionm anagment on EDGE_DBand etc, look at and LocalOutboxMaintanceJopb and decide what role



Those are gonna be the edge-db user with tdetails below,
EDGE_DB_DUTY_USERNAME -> A real payment platform operator, whuich has super credenitals
EDGE_DB_PAYMENT_SERVICE_USERNAME-> is representing payment-service web application client connection to edge-db, will have NPA_Business_Owner, on edge-db db-datareader,and db-datawriter,   no persmission for alter database or crete dderop dataase.
EDGE_DB_PAYMENT_EDGE_WORKERS_USERNAME -> is representing payment-edge-workers (LocalOutboxForwarder Job) job/ web application client connection to edge-db.db-datareader,and db-datawriter,   no persmission for alter database or crete dderop dataase.
so this db credentials are gonna be in helm runtime memory during deployment (kubectl get secrets -n payment edge-db-init-secret  -o json)
So all those operation is gonna be comp[leted during bootstrap], after db is up then it's thel iquibase turn.



--------------------------

central-db scenario

step 1- Db initialation with role craetions,username passwrod crendetials are created on db using the credentials defined in  central-db-init-secret.(Please
note that, when usrs are fcreated, db's tables are not created yet, so make sure to apply certain sql queries to grant our users already accesss to new tables as well)
step 2 after centedials are created and db is up then a light weight liquibase container runs the cjangelog files defined in central-db-changelog-configmap.xml.




similar to edge-db roles cratioon jjust names update accorndgluy


central=db
CENTRAL_DB_PAYMENT_CONSUMERS_USERNAME: ENC[AES256_GCM,data:O4CcBr+J6fImsoNW2CucZmxxXpZq2zcbc2ZoikVWZrSHEbb6pg==,iv:E8kGrSrzBS9zFxac0WaM8UozkSYPLHxElX4UDW1APZU=,tag:R6sHfUxIIf7sEQ/s9TX1+Q==,type:str]
CENTRAL_DB_PAYMENT_CONSUMERS_PASSWORD: ENC[AES256_GCM,data:GWgkn3cxPenR40p01H8v1fnwHmSeMyZOk8R+6aq7j8DJhi96uw==,iv:dW/8Fin7T38+f4BwwRq5gZUMLHdA1sZr44MX0k2qBGs=,tag:ULYeX0TiA11sMNUOYRCfJA==,type:str]
CENTRAL_DB_PAYMENT_CENTRAL_RELAY_USERNAME: ENC[AES256_GCM,data:88Dd7nPi+YFnzJBQNQvrK8EonydRMrQgNCj+rQmX0XUMTXch2Fowo8g=,iv:lMMFUgBX5upEiJ8ZWIAYAYc7Ty2YphLQpjspB56qv9c=,tag:whzHSqRG/g2BVtuQTbgsww==,type:str]
CENTRAL_DB_PAYMENT_CENTRAL_RELAY_PASSWORD: ENC[AES256_GCM,data:Tu3J3dnTcI1PQXqvDJGJZI0B8lsaSMU+Md7i+YD5CBGkTILP8pkdKJE=,iv:9M/kdwAj60y/hepIfqZ9Q91hcRtFDvoTI34Ir4beHFY=,tag:Y5Vv7P6cXzyDrfTRG9b5Gw==,type:str]
CENTRAL_DB_PAYMENT_EDGE_WORKERS_USERNAME: ENC[AES256_GCM,data:2Y5LTetK2dH4+D4WR7Z4Sh2wS94XC8O/HyIH5Z1UBFW3DwGaJru5kw==,iv:Um4UWBGkb+LaWVtdYETUO1j3UazHgkHP8/3Hmxc/QPE=,tag:fxAzCfUie4uATTrr46ZTLA==,type:str]
CENTRAL_DB_PAYMENT_EDGE_WORKERS_PASSWORD: ENC[AES256_GCM,data:yThvTdcxhByMKz2cUimkjHscbvYWEC+0WXdFhIJOuSxIpFoonYBL+Q==,iv:QIdifTmemR38l83X177oUcQ4VP62g6PNZcb1TQ1+5UM=,tag:CE15zWgaf5RsDk7Jo4PKYA==,type:str]
CENTRAL_DB_DUTY_USERNAME: ENC[AES256_GCM,data:pQAvF1MBjKdy29o2BWtelXm+bGvoOYK+,iv:YCO1L+lbdLfCTg4a2UtlxzsSksssKtvfKXwU5VobuX8=,tag:M/XAvin2vgh/UNREtkpGUw==,type:str]
CENTRAL_DB_DUTY_PASSWORD: ENC[AES256_GCM,data:CflZADZqWNH59iMsGBqzFY7SZ23fq75t,iv:ErI8ZOi0PRNwWXrbxghvQDUz6X3gXA6S1oTluwYFFjo=,tag:ML6AnvcWGOLng4g9q7a8dw==,type:str]
CENTRAL_DB_POSTGRES_PASSWORD: ENC[AES256_GCM,data:G7JmOPTOAY/xoEYwKhmLTBiJEHOWoZIg2zeVew==,iv:E3GtSSkzTCKmDMHE6ha3dvM2+p3qBuPX05Yu3+xxi4E=,tag:VMgzfBd18ZFLiH0xNCLVew==,type:str]
CENTAL_DB_MAINTENANCE_USERNAME -> A an applciationb or web service responsible for mainteneace task like partitionm anagment on CENTRAL_DB etc, look at and CentralOutboxMaintanceJopb and decide what role


So all those roles and users are gonna be created with the hardcoded roles for now , but with password and username from the @edge-cell-sops-secrets.yaml wjere simply be located in 



So and most important part is when i do start system via deploy-infra.yaml on azure, i should not have tocheck db's are created every time, so al this flow are part of intializion flow,and
given we also rely on declarative infrasracture aas code principle,and certain readiness probess, we will not be bothered by complexity of what sdtart before what et .






