#myRetail Case Study

##Background
For this case study, I wanted to challenge myself by building a microservice with something I'm vaguely familiar with (I've worked on Spray APIs before) and see if I could get something elegant (and working) up in a reasonable amount of time.

##Setup and Running
Just because this involves using an outside database, I really should do a docker composition.  But, in case I don't get time, and you're on OSX:

From root of repository:

1. `brew install sbt curl redis`
2. `sbt test` to run the specs
3. `redis-server` to start the pricing db service
4. `sbt run` to start the server
5. `./examples.sh` to run some curl commands
