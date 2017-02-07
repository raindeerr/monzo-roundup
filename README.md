An application to help with round ups (like Moneybox) that will calculate the round ups for a month and push a feed item so you can manually top up your Moneybox account.

To run you will need to create a Client ID and a Client secret on https://developers.monzo.com

Once you have these values they need to be passed into the application at runtime like so:

```bash
  sbt -Dmonzo.clientId=your_client_id -Dmonzo.clientSecret=your_client_secret "run 9000"   
```   

Once the application has started navigate to http://localhost:9000 and click the big button to start the log in process, the rest will happen automagically!