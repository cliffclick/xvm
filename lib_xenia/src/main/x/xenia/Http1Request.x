import ecstasy.collections.CaseInsensitive;

import net.IPAddress;
import net.SocketAddress;
import net.URI;

import web.AcceptList;
import web.Body;
import web.Header;
import web.HttpMessage;
import web.HttpMethod;
import web.MediaType;
import web.Protocol;
import web.Scheme;
import web.routing.UriTemplate;
import web.routing.UriTemplate.UriParameters;

import HttpServer.RequestInfo;


/**
 * An implementation of an HTTP/1 (i.e. 0.9, 1.0, 1.1) request, as received by a server, using the
 * raw request data provided by the `HttpServer.Handler` interface.
 */
const Http1Request(RequestInfo info, UriParameters matchResult)
        implements Request
        implements Header
        implements Body
    {
    assert()
        {
        // TODO handle non-simple bodies e.g. multi-part, streaming
        assert !info.containsNestedBodies();
        if (Byte[] bytes := info.getBodyBytes())
            {
            this.hasBody = True;
            this.bytes   = bytes;

            assert String[] contentTypes := info.getHeaderValuesForName("Content-Type");
            assert mediaType := MediaType.of(contentTypes[0]);
            }
        else
            {
            this.hasBody   = False;
            this.bytes     = [];
            this.mediaType = Text;  // whatever
            }
        }

    /**
     * Internal.
     */
    protected Boolean hasBody;


    // ----- HttpMessage interface -----------------------------------------------------------------

    @Override
    @RO Header header.get()
        {
        return this;
        }

    @Override
    Body? body
        {
        @Override
        Body? get()
            {
            return hasBody ? this : Null;
            }

        @Override
        void set(Body? body)
            {
            throw new ReadOnly();
            }
        }

    @Override
    Body ensureBody(MediaType mediaType, Boolean streaming=False)
        {
        assert Body body ?= this.body as "Body is absent";
        assert body.mediaType == mediaType as $"Body media type is {body.mediaType}";
        return body;
        }

    @Override
    immutable HttpMessage freeze(Boolean inPlace = False)
        {
        return this;
        }


    // ----- Request interface ---------------------------------------------------------------------

    @Override
    HttpMethod method
        {
        @Override
        HttpMethod get()
            {
            return info.getMethod();
            }

        @Override
        void set(HttpMethod method)
            {
            throw new ReadOnly();
            }
        }

    @Override
    Scheme scheme
        {
        @Override
        Scheme get()
            {
            return Scheme.byName.getOrNull(uri.scheme?)? : assert;
            }

        @Override
        void set(Scheme scheme)
            {
            throw new ReadOnly();
            }
        }

    @Override
    String authority
        {
        @Override
        String get()
            {
            return uri.authority ?: assert;
            }

        @Override
        void set(String authority)
            {
            throw new ReadOnly();
            }
        }

    @Override
    String path
        {
        @Override
        String get()
            {
            return uri.path?.toString() : assert;
            }

        @Override
        void set(String path)
            {
            throw new ReadOnly();
            }
        }

    @Override
    String requestLine.get()
        {
        // REVIEW
        return $"{method} {path} {protocol}";
        }

    @Override
    @Lazy URI uri.calc()
        {
        return info.getUri();
        }

    @Override
    @RO SocketAddress? client.get()
        {
        return (info.getClientAddress(), info.getClientPort());
        }

    @Override
    @RO SocketAddress? server.get()
        {
        return (info.getServerAddress(), info.getServerPort());
        }

    @Override
    Protocol? protocol.get()
        {
        String name = info.getProtocolString();
        return Protocol.byName.getOrCompute(name, () -> throw new IllegalState($"unknown protocol: {name}"));
        }

    @Override
    @Lazy Map<String, String|List<String>> queryParams.calc()
        {
        TODO CP
        }

    @Override
    UriParameters matchResult;

    @Override
    AcceptList accepts.get()
        {
        String accept = "";
        Loop: for (String add : valuesOf("Accept"))
            {
            add = add.trim();
            if (add != "")
                {
                accept = accept == ""
                        ? add
                        : $"{accept},{add}";
                }
            }

        assert AcceptList list := AcceptList.of(accept);
        return list;
        }

    @Override
    void setCookie(String name, String value)
        {
        throw new ReadOnly();
        }


    // ----- Header interface ----------------------------------------------------------------------

    @Override
    @RO Boolean isRequest.get()
        {
        return True;
        }

    @Override
    @RO Boolean modifiable.get()
        {
        return False;
        }

    @Override
    @Lazy List<String> names.calc()
        {
        return info.getHeaderNames();
        }

    @Override
    @Lazy List<Header.Entry> entries.calc()
        {
        return new List<Header.Entry>()
            {
            @Override
            Int size.get()
                {
                return names.size;
                }

            @Override
            @Op("[]") Header.Entry getElement(Int index)
                {
                assert:bounds 0 <= index < size;

                String name = names[index];
                assert String[] values := info.getHeaderValuesForName(name);
                return (name, values[0]);
                }
            };
        }

    @Override
    Iterator<String> valuesOf(String name, Char? expandDelim=Null)
        {
        Iterator<String> iter = entries.iterator().filter(kv -> CaseInsensitive.areEqual(kv[0], name))
                                                  .map(kv -> kv[1]);

        if (expandDelim != Null)
            {
            iter = iter.flatMap(s -> s.split(expandDelim).iterator());
            }

        return iter.map(s -> s.trim());
        }

    @Override
    conditional String firstOf(String name, Char? expandDelim=Null)
        {
        if (String[] values := info.getHeaderValuesForName(name))
            {
            String value = values.empty ? "" : values[0];
            if (expandDelim != Null)
                {
                values = value.split(expandDelim);
                value  = values[0].trim();
                }
            return True, value;
            }

        return False;
        }

    @Override
    conditional String lastOf(String name, Char? expandDelim=Null)
        {
        if (String[] values := info.getHeaderValuesForName(name))
            {
            String value = values.empty ? "" : values[values.size-1];
            if (expandDelim != Null)
                {
                values = value.split(expandDelim);
                value  = values[values.size-1].trim();
                }
            return True, value;
            }

        return False;
        }

    @Override
    void removeAll(String name)
        {
        throw new ReadOnly();
        }


    // ----- Body interface ------------------------------------------------------------------------

    @Override
    MediaType mediaType;

    @Override
    Byte[] bytes;

    @Override
    Body from(Object content)
        {
        throw new ReadOnly();
        }
    }