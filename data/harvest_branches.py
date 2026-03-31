#!/usr/bin/env python3
"""
Deutsche Bank Branch Harvester
===============================
Fetches branch data from Deutsche Bank IndexJson API for specified cities
and outputs normalized JSON data matching the schema used in branches.json.

Based on harvest methodology documented in:
docs/data/deutsche-bank-branch-harvest.md

Usage:
    python harvest_branches.py --cities "Karlsruhe,Wiesbaden,Münster" --output new_branches.json
    python harvest_branches.py --missing-cities --append
    python harvest_branches.py --city "Augsburg" --output augsburg_branches.json

Author: AI Agent (following harvest documentation)
Date: 2026-02-08
"""

import argparse
import json
import os
import sys
import time
from typing import Dict, List, Any, Optional
from urllib.parse import quote
from urllib.request import urlopen, Request
from urllib.error import URLError, HTTPError


# API Configuration (from harvest doc section 2.1)
BASE_URL = "https://www.deutsche-bank.de/cip/engine/pfb/content/gdata/Presentation/DbFinder/Home/IndexJson"
BRANCHES_PARAM = "PBCxFIN|PBCxINV|PBCxSEL|PBCxPRIBC|GAST"

# Missing cities from section 5 of harvest doc
MISSING_CITIES = [
    "Karlsruhe",
    "Wiesbaden",
    "Münster",
    "Augsburg",
    "Aachen",
    "Mönchengladbach",
    "Braunschweig",
    "Chemnitz",
    "Magdeburg",
    "Mainz",
    "Lübeck",
    "Erfurt",
    "Saarbrücken",
    "Potsdam",
    "Heidelberg",
    "Ulm",
    "Würzburg",
    "Regensburg",
    "Ingolstadt",
]

# Path to production branches.json file
BRANCHES_JSON_PATH = "java/bfa-service-resource/src/main/resources/data/branches.json"


def fetch_city_data(city: str, delay_seconds: float = 0.5) -> Dict[str, Any]:
    """
    Fetch raw branch data from Deutsche Bank API for a specific city.
    
    Args:
        city: City name to search for
        delay_seconds: Delay before making request (rate limiting)
    
    Returns:
        Raw JSON response from API
    
    Raises:
        HTTPError: If API request fails
    """
    time.sleep(delay_seconds)
    
    url = (
        f"{BASE_URL}"
        f"?label=BRANCH"
        f"&searchTerm={quote(city)}"
        f"&country="
        f"&branches={quote(BRANCHES_PARAM)}"
        f"&dataType=json"
    )
    
    try:
        req = Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urlopen(req, timeout=30) as response:
            data = json.loads(response.read().decode('utf-8'))
            return data
    except HTTPError as e:
        print(f"HTTP Error {e.code} for {city}: {e.reason}", file=sys.stderr)
        raise
    except URLError as e:
        print(f"URL Error for {city}: {e.reason}", file=sys.stderr)
        raise
    except Exception as e:
        print(f"Error fetching {city}: {e}", file=sys.stderr)
        raise


def parse_opening_hours(hours_list: List[Dict[str, Any]]) -> str:
    """
    Parse complex opening hours structure into simple string format.
    
    API returns a list of day objects with Item1 (branch hours) and Item2 (ATM hours).
    We focus on Item1 (branch opening hours).
    
    Args:
        hours_list: List of opening hours objects from API
    
    Returns:
        Formatted opening hours string like "Mo 10:00-12:30, 14:00-18:00; Di..."
    """
    if not hours_list or not isinstance(hours_list, list):
        return ""
    
    day_abbrev = {
        'Montag': 'Mo',
        'Dienstag': 'Di',
        'Mittwoch': 'Mi',
        'Donnerstag': 'Do',
        'Freitag': 'Fr',
        'Samstag': 'Sa',
        'Sonntag': 'So',
    }
    
    parts = []
    for day_obj in hours_list:
        # Extract branch hours (Item1), not ATM hours (Item2)
        item1 = day_obj.get('Item1')
        if not item1:
            continue
        
        day_name = item1.get('Day', '')
        day_short = day_abbrev.get(day_name, day_name[:2])
        
        # Check for morning and afternoon slots
        morning = item1.get('Morning')
        afternoon = item1.get('Afternoon')
        
        times = []
        if morning:
            from_time = morning.get('From', '')
            until_time = morning.get('Until', '')
            if from_time and until_time:
                times.append(f"{from_time}-{until_time}")
        
        if afternoon:
            from_time = afternoon.get('From', '')
            until_time = afternoon.get('Until', '')
            if from_time and until_time:
                times.append(f"{from_time}-{until_time}")
        
        if times:
            parts.append(f"{day_short} {', '.join(times)}")
    
    return "; ".join(parts) if parts else ""


def normalize_raw_record(raw_item: Dict[str, Any], city_query: str) -> Dict[str, Any]:
    """
    Normalize raw API response to match branches.json schema.
    
    Schema matching branches.json:
    - branchId: string
    - name: string
    - brand: string
    - address: string
    - city: string
    - postalCode: string
    - latitude: float
    - longitude: float
    - phone: string
    - openingHours: string
    - wheelchairAccessible: boolean
    - selfServices: list[string]
    - branchServices: list[string]
    - transitInfo: string
    - parkingInfo: string
    
    Args:
        raw_item: Raw item from API Items[] array
        city_query: City name used in query (for debugging)
    
    Returns:
        Normalized branch record
    """
    item = raw_item.get('Item', {})
    basic_data = item.get('BasicData', {})
    
    # Extract nested structures
    identifiers = basic_data.get('Identifiers', {})
    address = basic_data.get('Address', {})
    geo = basic_data.get('Geo', {})
    contact = basic_data.get('Contact', {})
    
    # Extract name (company name preferred)
    company_name = address.get('CompanyName')
    full_title = address.get('FullTitle')
    full_name = address.get('FullName')
    name = company_name or full_title or full_name or "Unknown Branch"
    
    # Extract brand (typically "Deutsche Bank" or "Postbank")
    brand = "Deutsche Bank"  # Default
    if company_name and "Postbank" in company_name:
        brand = "Postbank"
    
    # Extract coordinates (YCoord=lat, XCoord=lon)
    latitude = None
    longitude = None
    try:
        if geo.get('YCoord'):
            latitude = float(geo['YCoord'])
        if geo.get('XCoord'):
            longitude = float(geo['XCoord'])
    except (ValueError, TypeError):
        pass
    
    # Build full address string (street + house number)
    street_with_house = address.get('StreetWithHouseNo')
    if not street_with_house:
        street = address.get('Street', '')
        house_no = address.get('HouseNo', '')
        street_with_house = f"{street} {house_no}".strip()
    
    # Extract phone (normalize format)
    phone = contact.get('Phone') or contact.get('Tel') or ""
    
    # Extract opening hours (convert complex structure to simple string)
    opening_hours_raw = raw_item.get('OpeningHours', "")
    if isinstance(opening_hours_raw, list) and opening_hours_raw:
        # Parse complex opening hours structure
        opening_hours = parse_opening_hours(opening_hours_raw)
    elif isinstance(opening_hours_raw, str):
        opening_hours = opening_hours_raw
    else:
        opening_hours = ""
    
    # Extract wheelchair accessibility
    accessibility_list = raw_item.get('Accessibility', [])
    wheelchair_accessible = False
    if isinstance(accessibility_list, list):
        wheelchair_accessible = any(
            'rollstuhl' in str(item).lower() or 'wheelchair' in str(item).lower() 
            for item in accessibility_list
        )
    
    # Extract services
    self_services = raw_item.get('SelfServices', [])
    if not isinstance(self_services, list):
        self_services = []
    
    branch_services = raw_item.get('BranchServices', [])
    if not isinstance(branch_services, list):
        branch_services = []
    
    # Transit and parking info (may be in various fields)
    transit_info = raw_item.get('TransitInfo', "")
    parking_info = raw_item.get('ParkingInfo', "")
    
    # Build normalized record
    return {
        "branchId": str(identifiers.get('YMID3') or identifiers.get('YMIDDecoded') or 
                       identifiers.get('YMID') or identifiers.get('ProviderForeignKey') or ""),
        "name": f"{brand} {street_with_house}, {address.get('City', city_query)}",
        "brand": brand,
        "address": street_with_house,
        "city": address.get('City', city_query),
        "postalCode": address.get('Zip', ''),
        "latitude": latitude,
        "longitude": longitude,
        "phone": phone,
        "openingHours": opening_hours,
        "wheelchairAccessible": wheelchair_accessible,
        "selfServices": self_services,
        "branchServices": branch_services,
        "transitInfo": transit_info,
        "parkingInfo": parking_info,
    }


def harvest_cities(cities: List[str], verbose: bool = True) -> List[Dict[str, Any]]:
    """
    Harvest branch data for multiple cities.
    
    Args:
        cities: List of city names to fetch
        verbose: Print progress messages
    
    Returns:
        List of normalized branch records
    """
    all_branches = []
    summary = []
    
    for i, city in enumerate(cities, 1):
        if verbose:
            print(f"[{i}/{len(cities)}] Fetching {city}...", end=' ', flush=True)
        
        try:
            raw_data = fetch_city_data(city)
            items = raw_data.get('Items', [])
            
            normalized = [normalize_raw_record(item, city) for item in items]
            all_branches.extend(normalized)
            summary.append((city, len(normalized)))
            
            if verbose:
                print(f"✓ {len(normalized)} branches")
        
        except Exception as e:
            if verbose:
                print(f"✗ Failed: {e}")
            summary.append((city, 0))
    
    if verbose:
        print(f"\n{'='*60}")
        print("Summary:")
        for city, count in summary:
            status = "✓" if count > 0 else "✗"
            print(f"  {status} {city}: {count} branches")
        print(f"{'='*60}")
        print(f"Total: {len(all_branches)} branches harvested")
    
    return all_branches


def load_existing_branches(path: str) -> List[Dict[str, Any]]:
    """Load existing branches.json file."""
    if not os.path.exists(path):
        return []
    
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_branches(branches: List[Dict[str, Any]], path: str, pretty: bool = True):
    """Save branches to JSON file."""
    with open(path, 'w', encoding='utf-8') as f:
        if pretty:
            json.dump(branches, f, ensure_ascii=False, indent=2)
        else:
            json.dump(branches, f, ensure_ascii=False)


def merge_branches(existing: List[Dict[str, Any]], 
                   new: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Merge new branches with existing, avoiding duplicates by branchId.
    
    Args:
        existing: Existing branch records
        new: New branch records to add
    
    Returns:
        Merged list with duplicates removed
    """
    # Build index of existing branches by ID
    existing_ids = {b['branchId'] for b in existing if b.get('branchId')}
    
    # Add only new branches not already present
    merged = existing.copy()
    added_count = 0
    
    for branch in new:
        branch_id = branch.get('branchId')
        if branch_id and branch_id not in existing_ids:
            merged.append(branch)
            existing_ids.add(branch_id)
            added_count += 1
    
    print(f"Merged: {added_count} new branches added (duplicates skipped)")
    return merged


def main():
    parser = argparse.ArgumentParser(
        description='Harvest Deutsche Bank branch data from API',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Harvest specific cities
  %(prog)s --cities "Karlsruhe,Wiesbaden,Münster" --output new_branches.json
  
  # Harvest all missing cities from section 5
  %(prog)s --missing-cities --output missing_branches.json
  
  # Append to existing branches.json
  %(prog)s --missing-cities --append
  
  # Single city
  %(prog)s --city "Augsburg" --output augsburg.json
        """
    )
    
    # Input options
    input_group = parser.add_mutually_exclusive_group(required=True)
    input_group.add_argument(
        '--cities',
        help='Comma-separated list of city names'
    )
    input_group.add_argument(
        '--city',
        help='Single city name'
    )
    input_group.add_argument(
        '--missing-cities',
        action='store_true',
        help='Use predefined list of missing cities from harvest doc section 5'
    )
    
    # Output options
    output_group = parser.add_mutually_exclusive_group(required=True)
    output_group.add_argument(
        '--output',
        help='Output JSON file path'
    )
    output_group.add_argument(
        '--append',
        action='store_true',
        help='Append to existing branches.json (merges and deduplicates)'
    )
    
    # Additional options
    parser.add_argument(
        '--branches-json',
        default=BRANCHES_JSON_PATH,
        help=f'Path to branches.json file (default: {BRANCHES_JSON_PATH})'
    )
    parser.add_argument(
        '--delay',
        type=float,
        default=0.5,
        help='Delay between API requests in seconds (default: 0.5)'
    )
    parser.add_argument(
        '--quiet',
        action='store_true',
        help='Suppress progress messages'
    )
    
    args = parser.parse_args()
    
    # Determine city list
    if args.missing_cities:
        cities = MISSING_CITIES
        print(f"Using predefined missing cities list ({len(cities)} cities)")
    elif args.city:
        cities = [args.city]
    else:
        cities = [c.strip() for c in args.cities.split(',')]
    
    if not cities:
        print("Error: No cities specified", file=sys.stderr)
        return 1
    
    # Harvest data
    print(f"Harvesting {len(cities)} cities...")
    harvested = harvest_cities(cities, verbose=not args.quiet)
    
    if not harvested:
        print("Warning: No branches harvested", file=sys.stderr)
        return 1
    
    # Save or append
    if args.append:
        print(f"\nLoading existing branches from {args.branches_json}...")
        existing = load_existing_branches(args.branches_json)
        print(f"Existing: {len(existing)} branches")
        
        merged = merge_branches(existing, harvested)
        
        output_path = args.branches_json
        print(f"\nSaving merged data to {output_path}...")
        save_branches(merged, output_path)
        print(f"✓ Saved {len(merged)} branches (added {len(merged) - len(existing)} new)")
    else:
        output_path = args.output
        print(f"\nSaving to {output_path}...")
        save_branches(harvested, output_path)
        print(f"✓ Saved {len(harvested)} branches")
    
    return 0


if __name__ == '__main__':
    sys.exit(main())
