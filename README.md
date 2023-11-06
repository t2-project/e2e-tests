# e2e Test

This service represents an end-to-end test scenario for the T2-Project.

## Concept

This service provides an endpoint for the [UI Backend](https://github.com/t2-project/uibackend) and an endpoint for the [Payment service](https://github.com/t2-project/payment).
Those two services must be configured to call the e2e test service's endpoint instead of the Orchestrator respectively Payment provider.

This service takes the incoming requests from the UI Backend, looks at them and forwards them to the Orchestrator.
It takes the incoming request of the Payment service, replies in a defined way and asserts that the state of the order database, the inventory and the saga instance are in accordance to the given reply.

If the test service replied with success:
* saga instance: `endstate == true && compensation == false`
* order: `status == success`
* inventory: no reservations for `sessionId`

If the test service replied with failure:
* saga instance: `endstate == true && compensation == true`
* order : `status == failure`
* inventory : no reservations for `sessionId`

This service needs all other service up and running.
It also needs some request to come in, or else there is nothing to assert.

## Build and Run

Refer to the [Documentation](https://t2-documentation.readthedocs.io/en/latest/guides/deploy.html) on how to build, run or deploy the T2-Project services.

## HTTP Endpoints

* `/test`: UI Backend should POST to this endpoint instead of Orchestrator
* `/fakepay`: Payment should POST to this endpoint instead of the actual payment provider

## Usage

this is output.

explain output.

## Application Properties

### Properties for Eventuate

(they are required because the e2e test rely on eventuate's db connectors to access the saga instance db.)
c.f. [eventuate tram cdc](https://eventuate.io/docs/manual/eventuate-tram/latest/getting-started-eventuate-tram.html) for explanations.

| property | read from env var |
| -------- | ----------------- |
| spring.datasource.url | SPRING_DATASOURCE_URL |
| spring.datasource.username | SPRING_DATASOURCE_USERNAME |
| spring.datasource.password | SPRING_DATASOURCE_PASSWORD |
| spring.datasource.driver-class-name | SPRING_DATASOURCE_DRIVER_CLASS_NAME |
| eventuatelocal.kafka.bootstrap.servers | EVENTUATELOCAL_KAFKA_BOOTSTRAP_SERVERS |
| eventuatelocal.zookeeper.connection.string | EVENTUATELOCAL_ZOOKEEPER_CONNECTION_STRING |

### Other Properties

| property | read from env var | description |
| -------- | ----------------- | ----------- |
| spring.data.mongodb.uri | SPRING_MONGO_URL | url to mongodb of order and inventory. e2e test is only able to connect to _one_ mongo db (because it relies on spring's [MongoRepository interface](https://docs.spring.io/spring-data/mongodb/docs/current/api/org/springframework/data/mongodb/repository/MongoRepository.html) and i can't make them talk to different dbs.) *(hint: it's the entire url, not only the host)* |
| t2.e2etest.orchestrator.url | T2_ORCHESTRATOR_URL | orchestrator endpoint to forward saga requests to. |
