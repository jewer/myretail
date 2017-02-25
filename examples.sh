#!/usr/bin/env bash

# to bootstrap DB with some pricing info
curl -vv -X PUT -H "Content-Type: application/json" -d '{"value": 100, "currency":"USD"}' http://localhost:1337/product/13860428

#to get the name/price mashup
curl -vv http://localhost:1337/product/13860428
