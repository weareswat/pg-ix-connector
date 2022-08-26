# Payment Gatewayt -> InvoiceXpress Connector

[![Build Status](https://app.travis-ci.com/weareswat/pg-ix-connector.svg?branch=master)](https://app.travis-ci.com/weareswat/pg-ix-connector)

Makes the bridge between the payment gateway and InvoiceXpress.

### Usage

* `lein test` to run the tests
* `script/autotest` listen for file changes and is always running tests

**NOTE:**
* To run the tests, you must define the `pg-ix-test-account-name` and `pg-ix-api-key` env variables.

  - can add it on your profiles in `clojure.clj`
    or
  - run `PG_IX_TEST_ACCOUNT_NAME=<ix-account-name> PG_IX_API_KEY=<ix-api-key> script/autotest`
