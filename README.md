#myRetail Case Study

##Background
For this case study, I wanted to challenge myself by building a microservice with something I'm vaguely familiar with (I've worked on Spray APIs before) and see if I could get something elegant (and working) up in a reasonable amount of time.

##Setup and Running
From root of repository:

###If you are doing this locally on OSX

1. `brew install sbt curl redis`
2. `sbt test` to run the specs
3. `redis-server` to start the pricing db service
4. `sbt run` to start the server


###If you just want to run the server as a docker image:

1. change the IP address in `src/main/resources/application.conf` to the host machine's IP.  There's a clever way to expose this in docker-compose but I never figured it out.
2. `docker-compose build` (it'll take a while to build the redis/sbt images)
3. `docker-compose up` (it'll take a while to start sbt, wait until you see sbt say "Successfully bound to /0:0:0:0:0:0:0:0:1337")

Then run `./examples.sh` to see adding a price and getting a product.
