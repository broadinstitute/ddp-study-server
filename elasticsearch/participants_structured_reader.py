import json

with open("templates/participants_structured.json", encoding ='utf-8') as f:
    participants_structured = json.loads(f.read())
    mapping = {
        "properties": participants_structured['mappings']['_doc']['properties']
    }
    print(json.dumps(mapping))