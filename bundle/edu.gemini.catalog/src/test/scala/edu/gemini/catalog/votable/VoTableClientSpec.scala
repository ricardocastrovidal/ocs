package edu.gemini.catalog.votable

import edu.gemini.catalog.api.CatalogQuery
import edu.gemini.spModel.core.{Angle, Coordinates}
import org.apache.commons.httpclient.NameValuePair
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.time.NoTimeConversions

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class VoTableClientSpec extends SpecificationWithJUnit with VoTableClient with NoTimeConversions {

  "The VoTable client" should {

    val query = CatalogQuery(Coordinates.zero, Angle.fromDegrees(0.2))

    "produce query params" in {
      queryParams(query) should beEqualTo(Array(new NameValuePair("CATALOG", "sdss9"), new NameValuePair("RA", "0.000"), new NameValuePair("DEC", "0.000"), new NameValuePair("SR", "0.200")))
    }
    "make a query to a bad site" in {
      Await.result(doQuery(query, "unknown site"), 1.seconds) should throwA[IllegalArgumentException]
    }
    "be able to select the first successful of several futures" in {
      def f1 = Future { Thread.sleep(1000); throw new RuntimeException("oops") }
      def f2 = Future { Thread.sleep(2000); 42 } // this one should complete first
      def f3 = Future { Thread.sleep(3000); 99 }

      Await.result(selectOne(List(f1, f2, f3)), 3.seconds) should beEqualTo(42)
    }
    "make a query (skipped if it fails)" in {
      // We'll skip this one if it fails as it depends on the remote server and the content may change
      Await.result(VoTableClient.catalog(query), 5.seconds).isRight should beTrue.orSkip("Catalog maybe down")
    }

  }
}