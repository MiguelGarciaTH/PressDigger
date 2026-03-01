# Scribe News Person Crawler

A Python script that crawls Portuguese government member data from Wikidata.

## Overview

This script queries the Wikidata SPARQL endpoint to extract information about Portuguese government members, including ministers and secretaries of state from 1974 to the present. The data is exported to CSV and text files for reference and analysis.

## Features

- Queries Wikidata SPARQL endpoint
- Extracts Portuguese government member data
- Covers ministers and secretaries from 1974-present
- Exports results to CSV and text formats
- Includes detailed member information

## Output Files

- `portuguese_government_members.csv`: Compact CSV format
- `portuguese_government_members.txt`: Human-readable text format
- `portuguese_government_members_detailed.csv`: Detailed CSV with additional fields

## Requirements

- Python 3.x
- Required Python packages (install via pip):
  - SPARQLWrapper or requests (for SPARQL queries)
  - pandas (for CSV handling)

## Running

```bash
python person-crawler.py
```

The script will query Wikidata and generate the output files in the current directory.

## Data Source

Data is retrieved from [Wikidata](https://www.wikidata.org/) using SPARQL queries. The script focuses on Portuguese political figures who served in government positions since 1974 (the Carnation Revolution).
