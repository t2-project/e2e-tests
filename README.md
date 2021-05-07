# e2e-tests
e2e test for the t2 store.

## idea 

this service provides an endpoint for the ui backend servic and an endpoint for the payment service.
both ui backend and payment must be configured such that they call this services endpoint as orchestrator respectively payment provider.

the test service takes the incoming request from the ui backend and forwards them to the orchestrator.
it takes the incoming request of the payment service, replies in a defined way and asserts that the state of order, inventory, and saga instance are in accordance to the given reply.

that is, if the test service replies to the payment with success:
* saga instance: endstate == true && compensation == false
* order : status == success
* inventory : no reservations for sessionId

or, if the test service replies to the payment with failure:
* saga instance: endstate == true && compensation == true
* order : status == failure
* inventory : no reservations for sessionId

this service assumes that all other service are available. 
if they are not it is not this services problem to solve.

this service also requires some other component to trigger requests, e.g. a [loadgenerator](todo : insert link here). 
if there are no requests it is not this services problem that it can not assert anything.

## endpoints
* ``/test`` uibackend should POST to this endpoint instead of orchestrator to start a saga 
* ``/fakepay`` payment should POST to this endpoint instead of the credit institute

## application properies

### properties for eventuate:
(they are required because the e2e test rely on eventuate's db connectors to acces the saga instance db.)
c.f. [eventuate tram cdc](https://eventuate.io/docs/manual/eventuate-tram/latest/getting-started-eventuate-tram.html) for explanations.

property | read from env var | description |
-------- | ----------------- | ----------- |
spring.datasource.url | SPRING_DATASOURCE_URL |
spring.datasource.username | SPRING_DATASOURCE_USERNAME |
spring.datasource.password | SPRING_DATASOURCE_PASSWORD |
spring.datasource.driver-class-name | SPRING_DATASOURCE_DRIVER_CLASS_NAME |
eventuatelocal.kafka.bootstrap.servers | EVENTUATELOCAL_KAFKA_BOOTSTRAP_SERVERS |
eventuatelocal.zookeeper.connection.string | EVENTUATELOCAL_ZOOKEEPER_CONNECTION_STRING |


### other properties:
property | read from env var | description |
-------- | ----------------- | ----------- |
spring.data.mongodb.uri     |SPRING_MONGO_URL | url to mongodb of order and inventory. e2e test is only able to connect to _one_ mongo db (because it relies on spring's [MongoRepository interface](https://docs.spring.io/spring-data/mongodb/docs/current/api/org/springframework/data/mongodb/repository/MongoRepository.html) and i can't make them talk to different dbs.) *(also: memo to myself: this time its really the entire url, not only the host!!)*
t2.e2etest.orchestrator.url | T2_ORCHESTRATOR_URL | orchestrator endpoint to forward saga requests to.
