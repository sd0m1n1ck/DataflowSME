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

1.  Install [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
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

### Set up a project

1.  Go to [https://cloud.google.com/console](https://cloud.google.com/console).
1.  Enable billing and create a project. For this project:
    1.  Enable Google Dataflow API and BigQuery API.
    1.  Create a GCS bucket and a folder inside the bucket, to use as a staging location.
    1.  Create a BigQuery dataset to store the results.

### Prepare the environment

1. Authenticate to Google Cloud using the gcloud command and set the default credentials and 
default project. You will need to replace YOUR-PROJECT-ID with the id of the project 
you created before:

    ```shell
    $ gcloud auth login
    $ gcloud auth application-default login
    $ gcloud config set project YOUR-PROJECT-ID
    ```
    
1. Get the project name with Gcloud and set it as an env variable:
    ```shell
    $ export PROJECT=`gcloud config get-value project`
    ```
    
1. Set other environment variables
    ```shell
    $ export STAGING_FOLDER=gs://<path of the bucket and folder that you created before>
    $ export BIGQUERY_DATASET=<name of the dataset that you created before>
    $ export USER=`whoami`
    ```

### Download the code

* Clone the github repository

    ```shell
    $ git clone https://github.com/malo-denielou/DataflowSME.git
    $ cd DataflowSME
    ```

* Alternatively, download it from
    [here](https://github.com/malo-denielou/DataflowSME/archive/master.zip), and then
    unzip it:

    ```shell
    $ unzip DataflowSME-master.zip
    $ cd DataflowSME-master
    ```

## Exercise 0 (prework)

**Goal**: Use the provided Dataflow pipeline to import the input events from a file in GCS to
BigQuery and run simple queries on the result.

**Procedure**:

1.  Compile and run the pipeline:

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=org.apache.beam.examples.complete.game.Exercise0 \
         -Dexec.args="--project=$PROJECT \
                      --tempLocation=$STAGING_FOLDER \
                      --runner=DataflowRunner \
                      --outputDataset=$BIGQUERY_DATASET \
                      --outputTableName=events \
                      --input=gs://dataflow-samples/game/gaming_data1.csv"
    ```

1.  Navigate to the Dataflow UI (the link is printed in the terminal, look for
    `"To access the Dataflow monitoring console, please navigate to ..."`).

1.  Once the pipeline finishes (should take about 15-20 minutes), the Job Status
    on the UI changes to Succeeded.

1.  After the pipeline finishes, check the value of `ParseGameEvent/ParseErrors`
    aggregator on the UI. Scroll down in the Summary tab to find it. 

1.  Check the number of distinct teams in the created BigQuery table.

    ```shell
    $ bq query --project_id=$PROJECT \
        'select count(distinct team) from $BIGQUERY_DATASET.events;'
    ```

## Exercise 1

**Goal**: Use Dataflow to calculate per-user scores and write them to BigQuery. 

**Procedure**

1.  Modify `src/main/java8/org/apache/beam/examples/complete/game/Exercise1.java`

1.  Run the pipeline (using Direct runner):

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=org.apache.beam.examples.complete.game.Exercise1 \
         -Dexec.args="--project=$PROJECT \
                      --tempLocation=$STAGING_FOLDER \
                      --runner=DirectRunner \
                      --outputDataset=$BIGQUERY_DATASET \
                      --outputTableName=user_scores \
                      --input=gs://dataflow-sme-tutorial/gaming_data0.csv"
    ```

1.  Once the pipeline finishes successfully check the score for
    'user0_AmberDingo':

    ```shell
    $ bq query --project_id=$PROJECT \
        "select total_score from $BIGQUERY_DATASET.user_scores \
         where user = \"user0_AmberDingo\";"
    ```

1.  Rerun the pipeline on the Dataflow service, but remove the BigQuery table
    first:

    ```shell
    $ bq rm --project_id=$PROJECT $BIGQUERY_DATASET.user_scores
    ```

    and then execute the above `mvn` command with

    ```shell
        --runner=DataflowRunner
    ```

## Exercise 2

**Goal**: Use Dataflow to calculate per-hour team scores and write them to BigQuery.


**Procedure**: 

1.  Modify `src/main/java8/org/apache/beam/examples/complete/game/Exercise2.java`

1.  Run the pipeline:

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=org.apache.beam.examples.complete.game.Exercise2 \
         -Dexec.args="--project=$PROJECT \
                      --tempLocation=$STAGING_FOLDER \
                      --runner=DirectRunner \
                      --outputDataset=$BIGQUERY_DATASET \
                      --outputTableName=hourly_team_scores \
                      --input=gs://dataflow-sme-tutorial/gaming_data0.csv"
    ```

1.  Once the pipeline finishes successfully check the score for team
    'AmberDingo':

    ```shell
    $ bq query --project_id=$PROJECT \
        "select total_score from $BIGQUERY_DATASET.hourly_team_scores \
         where team = \"AmberDingo\" and window_start = \"2017-03-18 16:00:00 UTC\";"
    ```

## Exercise 3

**Goal**: Convert the previous pipeline to run in a streaming mode.

First, you need to set up the injector to publish scores via PubSub.

1.  Create and download a JSON key for Google Application Credentials. See
    [instructions](https://cloud.google.com/docs/authentication/getting-started).
    Make sure that the key's account has at least the following role:
    * Pub/Sub --> Editor
    
1.  Open a second terminal window. In this terminal run the commands listed 
in steps 2 and 3 of the section "Prepare the enviroment" to set the same variables 
as in the first terminal (you do **not** need to do step 1).

1.  In the new terminal set the new credentials by running:

    ```shell
    $ export GOOGLE_APPLICATION_CREDENTIALS=/path/to/your/credentials-key.json
    ```

1. Create a new topic:

    ```shell
    $ gcloud pubsub topics create game_events_$USER
    ```

1.  In the **second** terminal run the injector:

    ```shell
    $ mvn exec:java \
      -Dexec.mainClass="org.apache.beam.examples.complete.game.injector.Injector" \
      -Dexec.args="$PROJECT game_events_$USER none none"
    ```

Now complete the exercise so that it runs the pipeline from Exercise 2 in either
batch or streaming mode.

**Procedure**:

1.  Modify `src/main/java8/org/apache/beam/examples/complete/game/Exercise3.java`

1.  Run the pipeline in batch mode (this is equivalent to Exercise 2):

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=org.apache.beam.examples.complete.game.Exercise3 \
         -Dexec.args="--project=$PROJECT \
                      --tempLocation=$STAGING_FOLDER \
                      --runner=DirectRunner \
                      --outputDataset=$BIGQUERY_DATASET \
                      --outputTableName=hourly_team_scores \
                      --input=gs://dataflow-sme-tutorial/gaming_data0.csv"
    ```

1.  Run the pipeline in streaming mode on Dataflow service:

    ```shell
    $ mvn compile exec:java \
         -Dexec.mainClass=org.apache.beam.examples.complete.game.Exercise3 \
         -Dexec.args="--project=$PROJECT \
                      --tempLocation=$STAGING_FOLDER \
                      --runner=DataflowRunner \
                      --outputDataset=$BIGQUERY_DATASET \
                      --outputTableName=hourly_team_scores \
                      --topic=projects/$PROJECT/topics/game_events_$USER"
    ```

## Exercise 4

**Goal**: Use Dataflow to create a streaming per-minute BigQuery LeaderBoard of team
scores for events coming through PubSub. Some of the logged game events may be
late-arriving, if users play on mobile devices and go transiently offline for a
period.

**Procedure**:

1.  Modify
    `src/main/java8/org/apache/beam/examples/complete/game/Exercise4.java`
   
    You will need to make two sets of changes:
    *  Calculate the total score for every user and publish speculative results
every thirty seconds (under CalculateUserScores).
    * Calculate the team scores for each minute that the pipeline runs (under CalculateTeamScores).
    
1.  Make sure that the injector is running on the second terminal.

1.  Run the pipeline:

    ```shell
    $ mvn compile exec:java \
          -Dexec.mainClass=org.apache.beam.examples.complete.game.Exercise4 \
          -Dexec.args="--project=$PROJECT \
          --tempLocation=$STAGING_FOLDER \
          --runner=DataflowRunner \
          --topic=projects/$PROJECT/topics/game_events_$USER \
          --outputDataset=$BIGQUERY_DATASET \
          --outputTableName=leaderboard"
    ```

1.  Check the user and team scores, eg:

    ```shell
    $ bq query --project_id=$PROJECT \
         "SELECT * FROM [$BIGQUERY_DATASET.leaderboard_team] WHERE \
          timing=\"ON_TIME\" ORDER BY window_start"
    ```

## Exercise 5

**Goal**: Complete the CalculateSpammyUsers PTransform to (a) determine users who have a
score that is 2.5x the global average in each window, and then (b) use the
results to compute non-spammy team scores.

**Procedure**:

1.  Modify `src/main/java8/org/apache/beam/examples/complete/game/Exercise5.java`

1.  Make sure that the injector is running on the second terminal.

1.  Run the pipeline:

    ```shell
    $ mvn compile exec:java \
          -Dexec.mainClass=org.apache.beam.examples.complete.game.Exercise5 \
          -Dexec.args="--project=$PROJECT \
          --tempLocation=$STAGING_FOLDER \
          --runner=DataflowRunner \
          --topic=projects/$PROJECT/topics/game_events_$USER \
          --outputDataset=$BIGQUERY_DATASET \
          --outputTableName=despammed_scores"
    ```

1.  Check the de-spammed user scores:

    ```shell
    $ bq query --project_id=$PROJECT \
         "SELECT * FROM [$BIGQUERY_DATASET.despammed_scores] \
          ORDER BY window_start LIMIT 10"
    ```

## Exercise 6

**Goal**: Compute periodic global mean session durations for users.

**Procedure**:

1.  Modify `src/main/java8/org/apache/beam/examples/complete/game/Exercise6.java`

1.  Make sure that the injector is running on the second terminal.

1.  Run the pipeline:

    ```shell
    $ mvn compile exec:java \
          -Dexec.mainClass=org.apache.beam.examples.complete.game.Exercise6 \
          -Dexec.args="--project=$PROJECT \
          --tempLocation=$STAGING_FOLDER \
          --runner=DataflowRunner \
          --topic=projects/$PROJECT/topics/game_events_$USER \
          --outputDataset=$BIGQUERY_DATASET \
          --outputTableName=sessions"
    ```

1.  Let the pipeline run for over 5 minutes (the default window length)

1.  Check the de-spammed user scores and mean session lengths:

    ```shell
    $ bq query --project_id=$PROJECT \
         "SELECT * FROM [$BIGQUERY_DATASET.sessions] \
         ORDER BY window_start LIMIT 10"
    ```

## Exercise 7

**Goal**: Implement a pipeline that filters spammy users based on latency between 'game
play' events and 'game score' events.

**Procedure**: 

1.  Modify `src/main/java8/org/apache/beam/examples/complete/game/Exercise7.java`

1.  Stop the injector that you ran on the second terminal and run it again 
with additional output for play events.

    ```shell
    $ mvn exec:java \
      -Dexec.mainClass="org.apache.beam.examples.complete.game.injector.Injector" \
      -Dexec.args="$PROJECT game_events_$USER play_events_$USER none"
    ```

1.  Run the pipeline:

    ```shell
    $ mvn compile exec:java \
          -Dexec.mainClass=org.apache.beam.examples.complete.game.Exercise7 \
          -Dexec.args="--project=$PROJECT \
          --tempLocation=$STAGING_FOLDER \
          --runner=DataflowRunner \
          --topic=projects/$PROJECT/topics/game_events_$USER \
          --playEventsTopic=projects/$PROJECT/topics/play_events_$USER \
          --outputDataset=$BIGQUERY_DATASET \
          --outputTableName=exercise7"
    ```

1. Check the list of spammy users:

    ```shell
    $ bq query --project_id=$PROJECT \
         "SELECT * FROM [$BIGQUERY_DATASET.exercise7_bad_users] \
         ORDER BY time"
    ```