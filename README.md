# Tutorial for Cloud Dataflow

This is a collection of tutorial-style Dataflow exercises based on the [Dataflow
gaming
example](https://github.com/GoogleCloudPlatform/DataflowJavaSDK-examples/blob/master/src/main/java8/com/google/cloud/dataflow/examples/complete/game/README.md)
and inspired by the [Beam tutorial](https://github.com/eljefe6a/beamexample).

In the gaming scenario, many users play a fictional game, as members of
different teams, over the course of a day, and their events are logged for
processing.

The exercises either read the batch data from CSV files on GCS or the streaming
data from a [PubSub](https://cloud.google.com/pubsub/) topic (generated by the
included `Injector` program). All exercises write their output to BigQuery.

## Set up your environment

### Tools

1.  Install [Java 8](https://java.com/fr/download/)
1.  Install Maven (for
    [windows](https://maven.apache.org/guides/getting-started/windows-prerequisites.html),
    [mac](http://tostring.me/151/installing-maven-on-os-x/),
    [linux](http://maven.apache.org/install.html))
1.  Install Google [Cloud SDK](https://cloud.google.com/sdk/)
1.  Install an IDE such as [Eclipse](https://eclipse.org/downloads/) or
    [IntelliJ](https://www.jetbrains.com/idea/download/) (optional)
1.  To test your installation, open a terminal window and type:

    ```shell
    $ java -version
    $ mvn --version
    $ gcloud --version
    ```

### Google Cloud

1.  Go to https://cloud.google.com/console.
1.  Enable billing and create a project.
1.  Enable Google Dataflow API and BigQuery API.
1.  Create a GCS bucket in your project as a staging location.
1.  Create a BigQuery dataset in your project.

### Download the code

1.  Clone the github repository

    ```shell
    $ git clone https://github.com/mdvorsky/DataflowSME.git
    $ cd DataflowSME
    ```

## Exercise 0 (prework)

Use the provided Dataflow pipeline to import the input events from GCS to
BigQuery.

How many parse errors did you encounter? How many unique teams are present in
the dataset?

1.  Compile and run the pipeline. Note you need to replace YOUR-PROJECT,
    YOUR-STAGING-BUCKET and YOUR-BIGQUERY-DATASET with values from the previous
    section.

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=com.google.cloud.dataflow.tutorials.game.Exercise0 \
         -Dexec.args="--project=YOUR-PROJECT \
                      --stagingLocation=gs://YOUR-STAGING-BUCKET \
                      --runner=BlockingDataflowPipelineRunner \
                      --outputDataset=YOUR-BIGQUERY-DATASET \
                      --outputTableName=events \
                      --input=gs://dataflow-samples/game/gaming_data1.csv"
    ```

1.  Navigate to the Dataflow UI (the link is printed in the terminal, look for
    `"To access the Dataflow monitoring console, please navigate to ..."`).

1.  Once the pipeline finishes (should take about 15-20 minutes), the Job Status
    on the UI changes to Succeeded.

1.  After the pipeline finishes, check the value of `ParseGameEvent/ParseErrors`
    aggregator on the UI. Scroll down in the Summary tab to find it. This is the
    *first answer* to the exercise.

1.  Check the number of distinct teams in the created BigQuery table.

    ```shell
    $ bq query --project_id=YOUR-PROJECT \
        'select count(distinct team) from YOUR-BIGQUERY-DATASET.events;'
    ```

    This is the *second answer* to the exercise.

## Exercise 1

Use Dataflow to calculate per-user scores and write them to BigQuery.

What is the total score of the user 'user0_AmberDingo'?

1.  Modify
    `src/main/java8/com/google/cloud/dataflow/tutorials/game/Exercise1.java`
1.  Run the pipeline (using Direct runner)

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=com.google.cloud.dataflow.tutorials.game.Exercise1 \
         -Dexec.args="--project=YOUR-PROJECT \
                      --stagingLocation=gs://YOUR-STAGING-BUCKET \
                      --tempLocation=gs://YOUR-STAGING-BUCKET \
                      --runner=DirectPipelineRunner \
                      --outputDataset=YOUR-BIGQUERY-DATASET \
                      --outputTableName=user_scores \
                      --input=gs://dataflow-sme-tutorial/gaming_data0.csv"
    ```

1.  Once the pipeline finishes successfully check the score for
    'user0_AmberDingo':

    ```shell
    $ bq query --project_id=YOUR-PROJECT \
        'select total_score from YOUR-BIGQUERY-DATASET.user_scores \
         where user = "user0_AmberDingo";'
    ```

1.  Rerun the pipeline on the Dataflow service, but remove the BigQuery table
    first:

    ```shell
    $ bq rm --project_id=YOUR-PROJECT YOUR-BIGQUERY-DATASET.user_scores
    ```

    and then execute the above `mvn` command with

    ```shell
        --runner=BlockingDataflowPipelineRunner
    ```

## Exercise 2

Use Dataflow to calculate per-hour team scores and write them to BigQuery.

What was the total score of 'AmberDingo' at '2017-03-18 16:00:00 UTC'?

1.  Modify
    `src/main/java8/com/google/cloud/dataflow/tutorials/game/Exercise2.java`

1.  Run the pipeline

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=com.google.cloud.dataflow.tutorials.game.Exercise2 \
         -Dexec.args="--project=YOUR-PROJECT \
                      --stagingLocation=gs://YOUR-STAGING-BUCKET \
                      --tempLocation=gs://YOUR-STAGING-BUCKET \
                      --runner=DirectPipelineRunner \
                      --outputDataset=YOUR-BIGQUERY-DATASET \
                      --outputTableName=hourly_team_scores \
                      --input=gs://dataflow-sme-tutorial/gaming_data0.csv"
    ```

1.  Once the pipeline finishes successfully check the score for team
    'AmberDingo':

    ```shell
    $ bq query --project_id=YOUR-PROJECT \
        'select total_score from YOUR-BIGQUERY-DATASET.hourly_team_scores \
         where team = "AmberDingo" and window_start = "2017-03-18 16:00:00 UTC";'
    ```

## Exercise 3

Convert the previous pipeline to run in a streaming mode.

But first, we need to set up the injector to publish scores via PubSub.

1.  Create and download a JSON key for Google Application Credentials. See
    [instructions](https://developers.google.com/identity/protocols/application-default-credentials).

1.  Set the environment variables:

    ```shell
    $ export GOOGLE_APPLICATION_CREDENTIALS=/path/to/your/credentials-key.json
    ```

1.  Run the injector. (`game_events_$USER` is a unique PubSub topic name.)

    ```shell
    $ mvn exec:java \
      -Dexec.mainClass="com.google.cloud.dataflow.tutorials.game.injector.Injector" \
      -Dexec.args="YOUR-PROJECT game_events_$USER none none"
    ```

Now complete the exercise so that it runs the pipeline from Exercise 2 in either
batch or streaming mode.

1.  Modify
    `src/main/java8/com/google/cloud/dataflow/tutorials/game/Exercise3.java`

1.  Run the pipeline in batch mode (this is equivalent to Exercise 2).

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=com.google.cloud.dataflow.tutorials.game.Exercise3 \
         -Dexec.args="--project=YOUR-PROJECT \
                      --stagingLocation=gs://YOUR-STAGING-BUCKET \
                      --tempLocation=gs://YOUR-STAGING-BUCKET \
                      --runner=DirectPipelineRunner \
                      --outputDataset=YOUR-BIGQUERY-DATASET \
                      --outputTableName=hourly_team_scores \
                      --input=gs://dataflow-sme-tutorial/gaming_data0.csv"
    ```

1.  Run the pipeline in streaming mode on Dataflow service:

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=com.google.cloud.dataflow.tutorials.game.Exercise3 \
         -Dexec.args="--project=YOUR-PROJECT \
                      --stagingLocation=gs://YOUR-STAGING-BUCKET \
                      --tempLocation=gs://YOUR-STAGING-BUCKET \
                      --runner=BlockingDataflowPipelineRunner \
                      --outputDataset=YOUR-BIGQUERY-DATASET \
                      --outputTableName=hourly_team_scores \
                      --topic=projects/YOUR-PROJECT/topics/game_events_$USER \
                      --streaming"
    ```

## Exercise 4

Use Dataflow to create a streaming per-minute BigQuery LeaderBoard of team
scores for events coming through PubSub. Some of the logged game events may be
late-arriving, if users play on mobile devices and go transiently offline for a
period.

Part 1: Calculate the total score for every user and publish speculative results
every thirty seconds.

Part 2: Calculate the team scores for each minute that the pipeline runs.

1.  Modify
    `src/main/java8/com/google/cloud/dataflow/tutorials/game/Exercise4.java`
1.  Run the pipeline

    ```shell
    $ mvn compile exec:java \
          -Dexec.mainClass=com.google.cloud.dataflow.tutorials.game.Exercise4 \
          -Dexec.args="--project=YOUR-PROJECT \
          --stagingLocation=gs://YOUR-STAGING_BUCKET \
          --runner=BlockingDataflowPipelineRunner \
          --topic=projects/YOUR-PROJECT/topics/game_events_$USER \
          --outputDataset=YOUR-BIGQUERY-DATASET \
          --outputTableName=leaderboard"
    ```

1.  Check the user and team scores, eg:

    ```shell
    $ bq query --project_id=YOUR-PROJECT \
         'SELECT * FROM [YOUR-BIGQUERY-DATASET.leaderboard_team] WHERE \
          team="AmethystKookaburra" AND timing="ON_TIME" ORDER BY window_start'
    ```

## Exercise 5

Complete the CalculateSpammyUsers PTransform to (a) determine users who have a
score that is 2.5x the global average in each window, and then (b) use the
results to compute non-spammy team scores.

1.  Modify
    `src/main/java8/com/google/cloud/dataflow/tutorials/game/Exercise5.java`

1.  Run the pipeline

    ```shell
    $ mvn compile exec:java \
          -Dexec.mainClass=com.google.cloud.dataflow.tutorials.game.Exercise5 \
          -Dexec.args="--project=YOUR-PROJECT \
          --stagingLocation=gs://YOUR-STAGING_BUCKET \
          --runner=BlockingDataflowPipelineRunner \
          --topic=projects/YOUR-PROJECT/topics/game_events_$USER \
          --outputDataset=YOUR-BIGQUERY-DATASET \
          --outputTableName=despammed_scores"
    ```

1.  Check the de-spammed user scores:

    ```shell
    $ bq query --project_id=YOUR-PROJECT \
         'SELECT * FROM [YOUR-BIGQUERY-DATASET.despammed_scores] WHERE \
          team="AmethystKookaburra" ORDER BY window_start LIMIT 10'
    ```

## Exercise 6

Compute periodic global mean session durations for users.

1.  Modify
    `src/main/java8/com/google/cloud/dataflow/tutorials/game/Exercise6.java`

1.  Run the pipeline

    ```shell
    $ mvn compile exec:java \
          -Dexec.mainClass=com.google.cloud.dataflow.tutorials.game.Exercise6 \
          -Dexec.args="--project=YOUR-PROJECT \
          --stagingLocation=gs://YOUR-STAGING_BUCKET \
          --runner=BlockingDataflowPipelineRunner \
          --topic=projects/YOUR-PROJECT/topics/game_events_$USER \
          --outputDataset=YOUR-BIGQUERY-DATASET \
          --outputTableName=sessions"
    ```

1.  Check the de-spammed user scores and mean session lengths:

    ```shell
    $ bq query --project_id=YOUR-PROJECT \
         'SELECT * FROM [YOUR-BIGQUERY-DATASET.sessions] \
         ORDER BY window_start LIMIT 10'
    ```

## Exercise 7

Implement a pipeline that filters spammy users based on latency between 'game
play' events and 'game score' events.

1.  Modify
    `src/main/java8/com/google/cloud/dataflow/tutorials/game/Exercise7.java`

1.  Run the injector with additional output for play events.

    ```shell
    $ mvn exec:java \
      -Dexec.mainClass="com.google.cloud.dataflow.tutorials.game.injector.Injector" \
      -Dexec.args="YOUR-PROJECT game_events_$USER play_events_$USER none"
    ```

1.  Run the pipeline

    ```shell
    $ mvn compile exec:java \
          -Dexec.mainClass=com.google.cloud.dataflow.tutorials.game.Exercise7 \
          -Dexec.args="--project=YOUR-PROJECT \
          --stagingLocation=gs://YOUR-STAGING_BUCKET \
          --runner=BlockingDataflowPipelineRunner \
          --topic=projects/YOUR-PROJECT/topics/game_events_$USER \
          --playEventsTopic=projects/YOUR-PROJECT/topics/play_events_$USER \
          --outputDataset=YOUR-BIGQUERY-DATASET \
          --outputTableName=exercise7"
    ```
