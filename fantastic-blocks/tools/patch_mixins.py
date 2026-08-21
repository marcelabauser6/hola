import io, json, sys

path = sys.argv[1]
data = json.load(io.open(path, encoding="utf-8"))
mixins = data.get("mixins", [])
if "HangingEntityMixin" not in mixins:
    mixins.append("HangingEntityMixin")
    data["mixins"] = mixins
    io.open(path, "w", encoding="utf-8").write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    print("mixins.json -> anadido HangingEntityMixin")
else:
    print("mixins.json -> ya estaba")
print("mixins: " + ", ".join(data["mixins"]))
