import sys
import json


def has_analyzer():
    setting = json.loads(sys.argv[1])
    index = sys.argv[2]
    if 'analysis' in setting[index]['settings']['index'] \
            and 'analyzer' in setting[index]['settings']['index']['analysis'] \
            and 'case_insensitive_sort' in setting[index]['settings']['index']['analysis']['analyzer']:
        print(index+',')

if __name__ == "__main__":
    has_analyzer()
