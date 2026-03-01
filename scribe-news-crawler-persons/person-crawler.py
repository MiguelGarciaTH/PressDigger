#!/usr/bin/env python3
import requests
import pandas as pd
import time

SPARQL_ENDPOINT = "https://query.wikidata.org/sparql"
HEADERS = {"User-Agent": "gov-crawler/1.0"}

def run_query(query):
    for attempt in range(3):
        try:
            r = requests.get(
                SPARQL_ENDPOINT,
                params={"format": "json", "query": query},
                headers=HEADERS,
                timeout=90
            )
            r.raise_for_status()
            return r.json()
        except Exception as e:
            if attempt < 2:
                print(f"  Retry {attempt+1}/3...")
                time.sleep(3)
            else:
                raise

# Get all government positions in Portugal
QUERY_GOV_MEMBERS = """
SELECT DISTINCT ?person ?personLabel ?position ?positionLabel ?start WHERE {{
  ?person p:P39 ?statement.
  ?statement ps:P39 ?position;
             pq:P580 ?start.

  ?position wdt:P17 wd:Q45.

  FILTER(?start >= "{start_date}"^^xsd:dateTime && ?start < "{end_date}"^^xsd:dateTime)

  SERVICE wikibase:label {{ bd:serviceParam wikibase:language "pt,en". }}
}}
ORDER BY ?start
"""

def is_government_position(position_label):
    """Check if position is a government minister or secretary of state"""
    pos_lower = position_label.lower()

    # Include these
    include_keywords = [
        'ministro',           # Ministro das Finanças, etc.
        'secretário de estado',  # Secretário de Estado
        'primeiro-ministro',  # Prime Minister
        'prime minister'      # English fallback
    ]

    # Exclude these
    exclude_keywords = [
        'deputado',           # MPs
        'assembleia',         # Assembly members
        'presidente',         # Presidents (of municipalities, etc.)
        'embaixador',         # Ambassadors
        'bispo',             # Bishops
        'arcebispo',         # Archbishops
        'câmara municipal',  # Municipal chambers
        'junta de freguesia', # Parish councils
        'regional',          # Regional government
        'governador',        # Governors
    ]

    # Check if any include keyword is present
    has_include = any(keyword in pos_lower for keyword in include_keywords)

    # Check if any exclude keyword is present
    has_exclude = any(keyword in pos_lower for keyword in exclude_keywords)

    return has_include and not has_exclude

def get_members_by_year_range(start_year, end_year):
    start_date = f"{start_year}-01-01T00:00:00Z"
    end_date = f"{end_year}-12-31T23:59:59Z"

    query = QUERY_GOV_MEMBERS.format(
        start_date=start_date,
        end_date=end_date
    )

    data = run_query(query)

    members = []
    for item in data["results"]["bindings"]:
        person_name = item["personLabel"]["value"]
        position_name = item["positionLabel"]["value"]

        # Skip if name is just an ID (Q123456)
        if person_name.startswith("Q") and person_name[1:].isdigit():
            continue

        # Only include government positions
        if is_government_position(position_name):
            members.append({
                "name": person_name,
                "position": position_name,
                "start": item["start"]["value"][:10]
            })

    return members

def main():
    print("="*80)
    print("Portuguese Government Members (Ministers & Secretaries of State)")
    print("1974 - Present")
    print("="*80)
    print()

    all_members = []

    year_ranges = [
        (1974, 1980),
        (1980, 1990),
        (1990, 2000),
        (2000, 2010),
        (2010, 2020),
        (2020, 2027)
    ]

    for start_year, end_year in year_ranges:
        print(f"📥 Fetching {start_year}-{end_year}...", end=" ")
        try:
            members = get_members_by_year_range(start_year, end_year)
            all_members.extend(members)
            print(f"✅ {len(members)} appointments")
            time.sleep(2)
        except Exception as e:
            print(f"❌ Error: {e}")

    if not all_members:
        print("\n❌ No data found!")
        return

    # Create DataFrame
    df = pd.DataFrame(all_members)

    # Get unique names
    unique_names = sorted(df['name'].unique())

    print(f"\n{'='*80}")
    print(f"RESULTS")
    print(f"{'='*80}")
    print(f"✅ Total appointments: {len(df)}")
    print(f"✅ Unique people: {len(unique_names)}")

    # Save just names (one per line, alphabetically)
    with open("portuguese_government_members.txt", "w", encoding="utf-8") as f:
        for name in unique_names:
            f.write(f"{name}\n")

    # Save full details
    df_sorted = df.sort_values(['start', 'position', 'name'])
    df_sorted.to_csv("portuguese_government_members_detailed.csv", index=False)

    print(f"\n✅ Saved names to: portuguese_government_members.txt")
    print(f"✅ Saved details to: portuguese_government_members_detailed.csv")

    # Statistics
    print(f"\n{'='*80}")
    print(f"TOP 20 POSITIONS")
    print(f"{'='*80}")
    print(df['position'].value_counts().head(20).to_string())

    print(f"\n{'='*80}")
    print(f"SAMPLE: Most Recent 30 Appointments")
    print(f"{'='*80}")
    recent = df_sorted.tail(30)
    for _, row in recent.iterrows():
        print(f"{row['start']} | {row['name']:35} | {row['position']}")

    print(f"\n{'='*80}")
    print(f"COMPLETE NAME LIST (first 50)")
    print(f"{'='*80}")
    for i, name in enumerate(unique_names[:50], 1):
        print(f"{i:3}. {name}")

if __name__ == "__main__":
    main()