# Continue using moneybox while also using Monzo!
[![Build Status](https://travis-ci.org/adamtrousdale/monzo-roundup.svg?branch=master)](https://travis-ci.org/adamtrousdale/monzo-roundup)

## How it works

The app uses webhooks to be notified of new transactions, works out the round up value, makes a running total and puts a nice feed item in Monzo!

Once every 24 hours it will send the round ups to moneybox as one off deposits. Currently I have had to hardcode an appId for moneybox. This may be different for each user, haven't actually tested this yet.

## Using the app 

There is a hosted example at https://monzo-roundup.herokuapp.com that can be used. You have to auth with Monzo and also enter your moneybox login creds, these are encrypted so can't be viewed.

You can also run your own version, which requires you to set the following environment variables:

- `APPLICATION_SECRET` - needed by Play! for each deployment
- `MONGODB_URI` - URI for the mongo database
- `MONZO_CLIENT_ID` - Monzo API client ID
- `MONZO_CLIENT_SECRET` - Monzo API client secret
- `MONZO_REDIRECT_URI` - the URI to redirect to after Monzo auth
- `CRYPTO_CURRENT_KEY` - the key used for encrypting the account details
- `WEBHOOK_CALLBACK_URL` - the URL that Monzo will call when a transaction is created

You can pass these in at the command line like:

`sbt -DMONGODB_URI=this -DMONZO_CLIENT_ID=that "run 9000"`

and so on.

Please give me all feedback as this is something I've quickly pulled together and currently it works foo me and it'd be great if it works for others!
