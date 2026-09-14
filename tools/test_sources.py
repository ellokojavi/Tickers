#!/usr/bin/env python3
"""The source chain's own tests.

The generators produce every figure the app ships, so the code that reads a
source is load-bearing in a way that fails quietly: a number parsed wrongly
becomes a wrong UF for a day in 1994 that nobody will ever look at, and a
fallback that does not fire becomes a year of missing data. The seed tests
catch a corrupted *result*; these catch the reading of it, before a CI run is
spent finding out.

No dependency and no network: stdlib unittest against the response shapes, and
a fake source for the chain. Run from the repository root:

    python3 -m unittest discover -s tools -p "test_*.py"
"""
import os
import unittest

import sources


class ChileanNumbers(unittest.TestCase):
    """The CMF writes numbers the way Chile writes them, and the way Chile
    writes them is the way this app prints them: "40.880,36". These cases are
    the real payload the Android app captured from the live API, kept when that
    channel was removed."""

    def test_reads_the_captured_payload(self):
        self.assertEqual(sources._chilean_number("40.880,36"), 40880.36)
        self.assertEqual(sources._chilean_number("5.712,95"), 5712.95)

    def test_reads_values_with_no_thousands_separator(self):
        self.assertEqual(sources._chilean_number("937,17"), 937.17)
        self.assertEqual(sources._chilean_number("4,5"), 4.5)

    def test_reads_a_value_in_the_tens_of_millions(self):
        # The separator is dropped, not treated as a decimal point: getting
        # this backwards turns 72 million into 72.
        self.assertEqual(sources._chilean_number("72.478.736"), 72478736)

    def test_refuses_what_is_not_a_number(self):
        with self.assertRaises(ValueError):
            sources._chilean_number("sin dato")


class ResponseShape(unittest.TestCase):
    """The CMF names the array after its resource — "UFs" for the UF, something
    else for each of the others — and only the UF's name was ever verified
    against the live API. Finding the one list in the object is what lets a
    resource be added without guessing its plural."""

    def test_finds_the_rows_whatever_the_wrapper_is_called(self):
        for name in ("UFs", "Dolares", "Euros", "UTMs"):
            self.assertEqual(sources._rows_of({name: [1, 2], "Otro": "x"}), [1, 2])

    def test_survives_a_payload_with_no_rows_at_all(self):
        self.assertEqual(sources._rows_of({}), [])
        self.assertEqual(sources._rows_of({"Mensaje": "API key no valida"}), [])
        self.assertEqual(sources._rows_of(None), [])
        self.assertEqual(sources._rows_of("no soy un objeto"), [])


class TheChain(unittest.TestCase):
    def setUp(self):
        self._key = os.environ.pop("CMF_API_KEY", None)
        self._chain = sources.chain_for
        sources.served.clear()

    def tearDown(self):
        if self._key is not None:
            os.environ["CMF_API_KEY"] = self._key
        # Restored, not deleted: deleting the attribute removes the real
        # function from the module and every later test in the file sees the
        # hole. It did, the first time this ran.
        sources.chain_for = self._chain
        sources.served.clear()

    def _serve(self, *pairs):
        """Stand in for the real chain with sources that do as they are told."""
        sources.chain_for = lambda _code: list(pairs)

    def test_without_a_key_the_official_source_is_not_even_tried(self):
        # Not a preference: a request with no key is a request that cannot
        # succeed, and trying it would only slow every build down.
        self.assertEqual([n for n, _ in sources.chain_for("uf")], ["mindicador.cl"])

    def test_with_a_key_the_official_source_goes_first(self):
        os.environ["CMF_API_KEY"] = "cualquier-cosa"
        self.assertEqual([n for n, _ in sources.chain_for("uf")], ["CMF", "mindicador.cl"])

    def test_a_blank_key_counts_as_no_key(self):
        # An unset GitHub secret interpolates to an empty string rather than
        # vanishing, so this is the shape the failure actually takes.
        os.environ["CMF_API_KEY"] = "   "
        self.assertEqual([n for n, _ in sources.chain_for("uf")], ["mindicador.cl"])

    def test_a_failing_source_hands_the_year_to_the_next_one(self):
        def broken(_year):
            raise OSError("la fuente esta caida")

        def working(_year):
            return [{"fecha": "2026-09-13", "valor": 40918.25}]

        self._serve(("rota", broken), ("buena", working))
        rows, who = sources.fetch_year("uf", 2026, attempts=1)
        self.assertEqual(who, "buena")
        self.assertEqual(len(rows), 1)

    def test_an_empty_answer_moves_on_rather_than_counting_as_data(self):
        # A source that answers "nothing for 1977" is not an error, but it is
        # not an answer either: the next source may well have that year.
        self._serve(
            ("vacia", lambda _y: []),
            ("buena", lambda _y: [{"fecha": "1977-08-01", "valor": 33.71}]),
        )
        rows, who = sources.fetch_year("uf", 1977, attempts=1)
        self.assertEqual(who, "buena")
        self.assertEqual(len(rows), 1)

    def test_a_year_nobody_can_serve_is_empty_rather_than_fatal(self):
        # The generator's own floor — it refuses to write a truncated series —
        # is what turns this into a failed build, and it can only do that if it
        # gets to run.
        self._serve(("rota", lambda _y: []))
        rows, who = sources.fetch_year("uf", 2026, attempts=1)
        self.assertEqual((rows, who), ([], None))

    def test_records_who_answered_for_the_file_header(self):
        self._serve(("CMF", lambda _y: [{"fecha": "2026-01-01", "valor": 1}]))
        sources.fetch_year("uf", 2025, attempts=1)
        sources.fetch_year("uf", 2026, attempts=1)
        self.assertEqual(sources.provenance(), "CMF (2 anos)")

    def test_says_so_when_nothing_answered(self):
        self.assertEqual(sources.provenance(), "sin fuente")


class ErrorMessages(unittest.TestCase):
    """The CMF refuses in JSON, not in a status line alone. Surfacing its own
    words is the difference between a log someone can act on — "API key no
    valida" — and one that just says 421."""

    def test_quotes_the_api_s_own_refusal(self):
        import io
        import urllib.error

        error = urllib.error.HTTPError(
            "https://api.cmfchile.cl/", 421, "", {},
            io.BytesIO(b'{"CodigoHTTP":421,"CodigoError":91,"Mensaje":"API key no valida"}'),
        )
        self.assertEqual(sources._explain(error), "HTTP 421: API key no valida")

    def test_falls_back_to_the_status_when_the_body_is_not_json(self):
        import io
        import urllib.error

        error = urllib.error.HTTPError(
            "https://api.cmfchile.cl/", 500, "", {}, io.BytesIO(b"<html>boom</html>"),
        )
        self.assertEqual(sources._explain(error), "HTTP 500")

    def test_reports_a_plain_failure_as_itself(self):
        self.assertEqual(sources._explain(OSError("timed out")), "timed out")


if __name__ == "__main__":
    unittest.main()
