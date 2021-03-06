/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.beam.examples.complete.game;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import java.util.ArrayList;
import java.util.List;
import org.apache.beam.examples.complete.game.solutions.Exercise3.ReadGameEvents;
import org.apache.beam.examples.complete.game.utils.ChangeMe;
import org.apache.beam.examples.complete.game.utils.GameEvent;
import org.apache.beam.examples.complete.game.utils.Options;
import org.apache.beam.runners.dataflow.DataflowRunner;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.extensions.gcp.options.GcpOptions;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.CreateDisposition;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.WriteDisposition;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.StreamingOptions;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.Mean;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.IntervalWindow;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sixth in a series of coding exercises in a gaming domain.
 *
 * <p>This exercise introduces session windows.
 *
 * <p>See README.md for details.
 */
public class Exercise6 {

  private static final Logger LOG = LoggerFactory.getLogger(Exercise6.class);

  /**
   * Calculate and output an element's session duration.
   */
  private static class UserSessionInfoFn extends DoFn<KV<String, Integer>, Integer> {

    @ProcessElement
    public void processElement(ProcessContext c, BoundedWindow window) {
      IntervalWindow w = (IntervalWindow) window;
      int duration = new Duration(w.start(), w.end()).toPeriod().toStandardMinutes().getMinutes();
      c.output(duration);
    }
  }

  /**
   * Options supported by {@link Exercise6}.
   */
  interface Exercise6Options extends Options, StreamingOptions {

    @Description("Numeric value of gap between user sessions, in minutes")
    @Default.Integer(1)
    Integer getSessionGap();

    void setSessionGap(Integer value);

    @Description(
        "Numeric value of fixed window for finding mean of user session duration, " + "in minutes")
    @Default.Integer(5)
    Integer getUserActivityWindowDuration();

    void setUserActivityWindowDuration(Integer value);
  }

  public static void main(String[] args) throws Exception {

    Exercise6Options options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(Exercise6Options.class);
    // Enforce that this pipeline is always run in streaming mode.
    options.setStreaming(true);
    options.setRunner(DataflowRunner.class);
    Pipeline pipeline = Pipeline.create(options);

    TableReference sessionsTable = new TableReference();
    sessionsTable.setDatasetId(options.getOutputDataset());
    sessionsTable.setProjectId(options.as(GcpOptions.class).getProject());
    sessionsTable.setTableId(options.getOutputTableName());

    PCollection<GameEvent> rawEvents = pipeline.apply(new ReadGameEvents(options));

    // Extract username/score pairs from the event stream
    PCollection<KV<String, Integer>> userEvents =
        rawEvents.apply(
            "ExtractUserScore",
            MapElements
                .into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.integers()))
                .via((GameEvent gInfo) -> KV.<String, Integer>of(gInfo.getUser(),
                    gInfo.getScore())));

    // [START EXERCISE 6]:
    // Detect user sessions-- that is, a burst of activity separated by a gap from further
    // activity. Find and record the mean session lengths.
    // This information could help the game designers track the changing user engagement
    // as their set of games changes.
    userEvents
        // Window the user events into sessions with gap options.getSessionGap() minutes. Make sure
        // to use an outputTimeFn that sets the output timestamp to the end of the window. This will
        // allow us to compute means on sessions based on their end times, rather than their start
        // times.
        // JavaDoc:
        //   - https://beam.apache.org/documentation/sdks/javadoc/2.0.0/org/apache/beam/sdk/transforms/windowing/Sessions.html
        //   - https://beam.apache.org/documentation/sdks/javadoc/2.0.0/org/apache/beam/sdk/transforms/windowing/Window.html
        // Note: Pay attention to the withTimestampCombiner method on Window.
        .apply("WindowIntoSessions",
            /* TODO: YOUR CODE GOES HERE */
            new ChangeMe<PCollection<KV<String, Integer>>, KV<String, Integer>>())
        // For this use, we care only about the existence of the session, not any particular
        // information aggregated over it, so the following is an efficient way to do that.
        .apply(Combine.perKey(x -> 0))
        // Get the duration per session.
        .apply("UserSessionActivity", ParDo.of(new UserSessionInfoFn()))
        // Note that the output of the previous transform is a PCollection of session durations
        // (PCollection<Integer>) where the timestamp of elements is the end of the window.
        //
        // Re-window to process groups of session sums according to when the sessions complete.
        // In streaming we don't just ask "what is the mean value" we must ask "what is the mean
        // value for some window of time". To compute periodic means of session durations, we
        // re-window the session durations.
        .apply("WindowToExtractSessionMean",
            /* TODO: YOUR CODE GOES HERE */
            new ChangeMe<PCollection<Integer>, Integer>())
        // Find the mean session duration in each window.
        .apply(Mean.<Integer>globally().withoutDefaults())
        // Write this info to a BigQuery table.
        .apply("FormatSessions", ParDo.of(new FormatSessionWindowFn()))
        .apply(
            BigQueryIO.writeTableRows().to(sessionsTable)
                .withSchema(FormatSessionWindowFn.getSchema())
                .withCreateDisposition(CreateDisposition.CREATE_IF_NEEDED)
                .withWriteDisposition(WriteDisposition.WRITE_APPEND));
    // [END EXERCISE 6]:

    PipelineResult result = pipeline.run();
    result.waitUntilFinish();
  }

  /**
   * Format a KV of session and associated properties to a BigQuery TableRow.
   */
  static class FormatSessionWindowFn extends DoFn<Double, TableRow> {

    @ProcessElement
    public void processElement(ProcessContext c, BoundedWindow window) {
      IntervalWindow w = (IntervalWindow) window;
      TableRow row =
          new TableRow()
              .set("window_start", w.start().getMillis() / 1000)
              .set("mean_duration", c.element());
      c.output(row);
    }

    static TableSchema getSchema() {
      List<TableFieldSchema> fields = new ArrayList<>();
      fields.add(new TableFieldSchema().setName("window_start").setType("TIMESTAMP"));
      fields.add(new TableFieldSchema().setName("mean_duration").setType("FLOAT"));
      return new TableSchema().setFields(fields);
    }
  }
}
