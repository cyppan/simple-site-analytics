# Serverless site analytics using Clojure nbb and AWS

A simple Site analytics API with Clojure nbb running on AWS Lambda and API Gateway using [the Serverless Framework](https://www.serverless.com/framework/docs).

See the following article for more details: https://loop-code-recur.io/simple-site-analytics-with-serverless-clojure/

The Clojurescript code is interpreted at runtime on node.js directly without any compilation step thanks to [nbb](https://github.com/babashka/nbb) (at the cost of more latency).

The following AWS resources are used:
- a DynamoDB table SiteStatistics used to store view counters per day and url
- a Lambda which increments counters, to call to increment the views counters
- a Lambda which returns a html page showing the statistics for the last seven days
- Two API Gateway HTTP endpoints proxying to the lambdas (`POST /track` and `GET /dashboard`)

Everything is managed through Serverless, see the serverless.yml file.

## Features

<img width="1728" alt="Capture d’écran 2022-01-30 à 18 15 42" src="https://user-images.githubusercontent.com/1446201/151723509-4b42d855-72bb-4fb3-b771-16c90be3edb4.png">

Allow to count and visualize your site URLs views by day. The tracker is highly resilient and scalable thanks to AWS, find more info about costs in the "Deployment" part (should be free for small websites in the free tier period, very cheap after that).

There is a basic support for a "utm_source" query params to add in your url when you share them (ex: https://mysite.com?utm_source=twitter).    
Note that with the current code, only the values "twitter" and "slack" will be shown in the dashboard (but others will still be counted in the Dynamo table).

## Configuration

CORS are enabled for the `/track` HTTP endpoint, you should modify the allowed domain in the serverless.yml file and in the handlers/track.cljs with the one you want to track (search and replace "https://www.loop-code-recur.io" with your site domain).

You can also modify the AWS region in which the stack is deployed (currently "eu-west-3" aka Paris).

## Deployment

Install the serverless framework 
```
$ npm install -g serverless
```

Given that you have valid aws credentials configured in your aws CLI (or [configured otherwise](https://www.serverless.com/framework/docs/providers/aws/cli-reference/config-credentials)) you can run:
```
$ serverless deploy
```
To see the created endpoints' URL, you can run
```
$ serverless info
```
Note the endpoints in the form https://xxxxxxx.execute-api.eu-west-3.amazonaws.com

### Costs

If your AWS account is less than a year old, the costs should be covered by the AWS free tier unless you have a high traffic on your website (more that 1 million visits per month), you can find more info about AWS free tier quotas [here](https://aws.amazon.com/fr/free/?all-free-tier.sort-by=item.additionalFields.SortRank&all-free-tier.sort-order=asc&awsf.Free%20Tier%20Types=*all&awsf.Free%20Tier%20Categories=*all).    
For maximal costs saving, you probably want to add a TTL on the DynamoDB items, it's easy to add. Feel free to open an issue if you want me to add support for it (or even better: make a PR ;) ).

At some point a stack based on a stream of tracking events stored into BigQuery (serverless too) would probably make more sense and unlock a lot of new usages.

## Integration

On the wep pages for which you want to track views, you can add the following javascript code:
```html
<script type="text/javascript">
  fetch('https://xxxxxxx.execute-api.eu-west-3.amazonaws.com/track', {
    method: 'post', 
    mode: 'cors', 
    headers: {"Content-Type": "application/json"}, 
    body: JSON.stringify({
      url: document.querySelector("link[rel='canonical']").getAttribute("href"),
      utm_source: new URLSearchParams(window.location.search).get("utm_source")
    })
  });
</script>
```
Make sure that you have a valid canonical URL configured in a link tag.

You can see the (public) statistics dashboard in your browser at https://xxxxxxx.execute-api.eu-west-3.amazonaws.com/dashboard

## Local development

Given that you have valid AWS credentials configured in the aws CLI, you can simply start a nbb REPL.

At the moment Calva is the only one to support a nbb REPL, just use the "jack-in" command, choose "nbb", and press escape when it asks you the REPL url to connect.

Now you should be able to send forms in the REPL and send dynamo commands from your local computer.
