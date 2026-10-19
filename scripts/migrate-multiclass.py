#!/usr/bin/env python3
"""
One-off migration of the single-class data into the first class (docs/MULTI-CLASS.md).

What it does - ADDITIVE ONLY, nothing is deleted and no existing attribute changes:
  1. Saves every item of the table to a JSON file (refuses to go on if that fails).
  2. Creates the class record  CLASS#<id> / CLASS  (name, public-link token, and the payment
     details copied from the treasurer's parent record) - only if it doesn't exist yet.
  3. Creates a treasurer record CLASS#<id> / TREASURER#<email> for every parent record with
     role TREASURER - only if it doesn't exist yet.
  4. Adds the attribute classId=<id> to every student, parent, collection and ledger entry
     that doesn't have one yet (an UpdateItem that sets only that attribute, conditioned on
     the item existing and having no other class).
  5. Verifies: re-reads the whole table and checks, item by item, that every item from the
     backup still exists with every original attribute unchanged, that the items above now
     carry the classId, and that nothing else was added. Any difference -> exit code 1.

Safe to run more than once (it only completes what's missing) - and it must be run again
right after the new version is deployed, to pick up items the old version wrote meanwhile.
The previous app version ignores the extra attribute and the new records, so rolling back is
just deploying the previous image - no data restore needed.

Dry run by default; pass --apply to write.

  python3 -m venv .venv && .venv/bin/pip install boto3
  # local (Dev Services Localstack):
  AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test .venv/bin/python scripts/migrate-multiclass.py \
      --table groszdogrosza --endpoint-url http://localhost:<port> [--apply]
  # production:
  AWS_PROFILE=groszdogrosza .venv/bin/python scripts/migrate-multiclass.py --table groszdogrosza-prod [--apply]
"""
import argparse
import datetime
import json
import os
import secrets
import sys

import boto3

CLASS_OWNED = ("STUDENT", "PARENT", "COLLECTION")


def scan_all(client, table):
    items, kwargs = [], {"TableName": table}
    while True:
        page = client.scan(**kwargs)
        items.extend(page["Items"])
        if "LastEvaluatedKey" not in page:
            return items
        kwargs["ExclusiveStartKey"] = page["LastEvaluatedKey"]


def key_of(item):
    return (item["pk"]["S"], item["sk"]["S"])


def is_class_owned(item):
    pk, sk = key_of(item)
    return sk in CLASS_OWNED or (pk.startswith("STUDENT#") and sk.startswith("LEDGER#"))


def kind_of(item):
    pk, sk = key_of(item)
    return "LEDGER" if sk.startswith("LEDGER#") else sk.split("#")[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--table", required=True)
    parser.add_argument("--endpoint-url")
    parser.add_argument("--region", default="eu-central-1")
    parser.add_argument("--class-id", default="sp34-2a")
    parser.add_argument("--class-name", default="SP 34 - 2A")
    parser.add_argument("--backup-dir", default=os.path.expanduser("~/groszdogrosza-backups"))
    parser.add_argument("--apply", action="store_true", help="actually write (default: dry run)")
    args = parser.parse_args()

    client = boto3.client("dynamodb", region_name=args.region, endpoint_url=args.endpoint_url)
    class_id = args.class_id
    mode = "APPLY" if args.apply else "DRY RUN"
    print(f"== {mode}: table {args.table}{' @ ' + args.endpoint_url if args.endpoint_url else ''}, class {class_id}")

    # 1. Backup - always, also on a dry run.
    before = scan_all(client, args.table)
    os.makedirs(args.backup_dir, mode=0o700, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = os.path.join(args.backup_dir, f"{args.table}-{stamp}-before-migration.json")
    with open(backup, "w") as f:
        json.dump({"Items": before}, f)
    os.chmod(backup, 0o600)
    with open(backup) as f:
        if len(json.load(f)["Items"]) != len(before):
            sys.exit("Backup file doesn't contain every item - stopping before any change.")
    print(f"backup: {len(before)} items -> {backup}")

    other_classes = [i for i in before if is_class_owned(i) and "classId" in i and i["classId"]["S"] != class_id]
    if other_classes:
        sys.exit(f"{len(other_classes)} items already belong to another class - this script is for the "
                 f"first migration only. Stopping without changes.")

    treasurers = [i for i in before if key_of(i)[1] == "PARENT" and i.get("role", {}).get("S") == "TREASURER"]
    if not treasurers:
        sys.exit("No treasurer parent record found - stopping without changes.")
    payment = next((t for t in treasurers if "S" in t.get("bankAccountNumber", {}) or "S" in t.get("blikPhoneNumber", {})), None)

    # 2. The class record.
    class_key = {"pk": {"S": f"CLASS#{class_id}"}, "sk": {"S": "CLASS"}}
    existing_class = client.get_item(TableName=args.table, Key=class_key).get("Item")
    created = []
    if existing_class:
        print(f"class record already there: {existing_class['name']['S']}")
    else:
        null = {"NULL": True}
        class_item = {
            **class_key,
            "id": {"S": class_id},
            "classId": {"S": class_id},
            "name": {"S": args.class_name},
            "publicToken": {"S": secrets.token_urlsafe(16)},
            "bankAccountNumber": payment["bankAccountNumber"] if payment and "S" in payment.get("bankAccountNumber", {}) else null,
            "blikPhoneNumber": payment["blikPhoneNumber"] if payment and "S" in payment.get("blikPhoneNumber", {}) else null,
            "createdAt": {"S": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")},
        }
        print(f"create class record: {args.class_name} (payment details copied: {payment is not None})")
        if args.apply:
            client.put_item(TableName=args.table, Item=class_item, ConditionExpression="attribute_not_exists(pk)")
        created.append(key_of(class_item))

    # 3. Treasurer records.
    for treasurer in treasurers:
        email = treasurer["email"]["S"].strip().lower()
        item = {"pk": {"S": f"CLASS#{class_id}"}, "sk": {"S": f"TREASURER#{email}"},
                "classId": {"S": class_id}, "email": {"S": email}}
        if client.get_item(TableName=args.table, Key={"pk": item["pk"], "sk": item["sk"]}).get("Item"):
            print(f"treasurer record already there: {email}")
            continue
        print(f"create treasurer record: {email}")
        if args.apply:
            client.put_item(TableName=args.table, Item=item, ConditionExpression="attribute_not_exists(pk)")
        created.append(key_of(item))

    # 4. classId on every class-owned item that lacks it.
    targets = [i for i in before if is_class_owned(i)]
    missing = [i for i in targets if "classId" not in i]
    counts = {}
    for item in missing:
        counts[kind_of(item)] = counts.get(kind_of(item), 0) + 1
    print(f"add classId to {len(missing)} of {len(targets)} items: {counts}")
    if args.apply:
        for item in missing:
            client.update_item(
                TableName=args.table,
                Key={"pk": item["pk"], "sk": item["sk"]},
                UpdateExpression="SET classId = :c",
                ConditionExpression="attribute_exists(pk) AND (attribute_not_exists(classId) OR classId = :c)",
                ExpressionAttributeValues={":c": {"S": class_id}},
            )

    if not args.apply:
        print("== dry run finished - nothing was written. Re-run with --apply to migrate.")
        return

    # 5. Verify against the backup.
    after = {key_of(i): i for i in scan_all(client, args.table)}
    problems = []
    for original in before:
        current = after.get(key_of(original))
        if current is None:
            problems.append(f"MISSING {key_of(original)}")
            continue
        for name, value in original.items():
            if current.get(name) != value:
                problems.append(f"CHANGED {key_of(original)} attribute {name}")
        if is_class_owned(original) and current.get("classId") != {"S": class_id}:
            problems.append(f"NO CLASS {key_of(original)}")
        extra = set(current) - set(original) - ({"classId"} if is_class_owned(original) else set())
        if extra:
            problems.append(f"UNEXPECTED ATTRIBUTES {key_of(original)}: {sorted(extra)}")
    new_keys = set(after) - {key_of(i) for i in before}
    unexpected_new = new_keys - set(created)
    # Items the running app wrote in the meantime are fine but get reported.
    if unexpected_new:
        print(f"note: {len(unexpected_new)} items appeared that this run didn't create (written by the app "
              f"meanwhile?) - re-run to give them a class if needed: {sorted(unexpected_new)[:5]}")
    if problems:
        print(f"!! VERIFICATION FAILED - {len(problems)} problems:")
        for p in problems[:50]:
            print("   ", p)
        print(f"Nothing was deleted; the full pre-migration copy is in {backup}")
        sys.exit(1)
    print(f"verified: all {len(before)} original items present and unchanged, {len(targets)} now in class "
          f"{class_id}, {len(created)} records created. Total items: {len(after)}.")


if __name__ == "__main__":
    main()
