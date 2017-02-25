#!/usr/bin/env bash
curl -i -w'\n' -XPUT -d@- http://localhost:9990/admin/treode/schema << EOF
table product {
    id: 1;
    price: 100;
    currency: "USD"
};
EOF