import sys
import json

def extract():
    aliases = sys.argv[1]
    participants_structured = json.loads(aliases)
    filtered_indexes = list(filter(lambda x: x.startswith('participants_structured.'), participants_structured))
    for item in filtered_indexes:
        print(item)

if __name__ == "__main__":
    extract()
