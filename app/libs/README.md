# Vendored OpenMemories-Framework jars

From [ma1co/OpenMemories-Framework](https://github.com/ma1co/OpenMemories-Framework) at commit `f8df3504d8684433a331ee869e64f67cc8be9730` (2018-05-29, the last commit). The jars are byte-identical to the jitpack build of that commit (`com.github.ma1co.OpenMemories-Framework:{framework,stubs}:f8df350`).

| File | Gradle configuration | sha256 |
|---|---|---|
| `openmemories-framework-f8df350.jar` | `implementation` | `906599a1f201d356649d2c44efec41e2b13a7ef7643e9603557444be6fb320df` |
| `openmemories-stubs-f8df350.jar` | `compileOnly` | `397a720581b3056d6b3806f217b75b312ff712885e3ca085da7a015897440602` |

The stubs must stay `compileOnly`. The real `com.sony.*` classes come from the camera firmware, and packaging the stubs would shadow them.

Check: `sha256sum app/libs/*.jar`

## License

Both jars are under the MIT License (`LICENSE.txt` at that commit):

```
The MIT License (MIT)

Copyright (c) 2016 ma1co

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
