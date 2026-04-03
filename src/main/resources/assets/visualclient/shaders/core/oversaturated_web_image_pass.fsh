#version 150

layout(std140) uniform ShadertoyGlobals {
    vec4 Resolution;
    vec4 Mouse;
    vec4 TimeData;
    vec4 ChannelResolution0;
    vec4 ChannelResolution1;
    vec4 ChannelResolution2;
    vec4 ChannelResolution3;
};

uniform sampler2D iChannel0;
uniform sampler2D iChannel1;
uniform sampler2D iChannel2;
uniform sampler2D iChannel3;

in vec2 texCoord;
out vec4 fragColor;

const float pi = acos(-1.0);
const float tau = 2.0*pi;
const float planeDist = 0.5;
const float furthest = 6.0;
const float fadeFrom = 4.0;
const float cutOff = 0.975;
const vec3 L = vec3(0.299, 0.587, 0.114);
const vec2 pathA = vec2(0.31, 0.41);
const vec2 pathB = vec2(sqrt(2.0), 1.0);
const vec4 U = vec4(0.0, 1.0, 2.0, 3.0);
const vec2 off6[6] = vec2[6](
    vec2(1.0, 0.0),
    vec2(0.5, 0.8660254),
    vec2(-0.5, 0.8660254),
    vec2(-1.0, 0.0),
    vec2(-0.5, -0.8660254),
    vec2(0.5, -0.8660254)
);
const vec2 noff6[6] = vec2[6](
    vec2(-1.0, 0.0),
    vec2(-0.5, 0.5),
    vec2(0.5, 0.5),
    vec2(1.0, 0.0),
    vec2(0.5, -0.5),
    vec2(-0.5, -0.5)
);

#define BEZIER
#define TIME TimeData.x
#define RESOLUTION vec2(Resolution.x, Resolution.y)
#define ROT(a) mat2(cos(a), sin(a), -sin(a), cos(a))

vec3 offset(float z) {
    return vec3(pathB*sin(pathA*z), z);
}

vec3 doffset(float z) {
    return vec3(pathA*pathB*cos(pathA*z), 1.0);
}

vec3 ddoffset(float z) {
    return vec3(-pathA*pathA*pathB*sin(pathA*z), 0.0);
}

float tanh_approx(float x) {
    float x2 = x*x;
    return clamp(x*(27.0 + x2)/(27.0 + 9.0*x2), -1.0, 1.0);
}

vec2 hextile(inout vec2 p) {
    const vec2 sz = vec2(1.0, sqrt(3.0));
    const vec2 hsz = 0.5*sz;

    vec2 p1 = mod(p, sz)-hsz;
    vec2 p2 = mod(p - hsz, sz)-hsz;
    vec2 p3 = dot(p1, p1) < dot(p2, p2) ? p1 : p2;
    vec2 n = ((p3 - p + hsz)/sz);
    p = p3;

    n -= vec2(0.5);
    return round(n*2.0)*0.5;
}

float hexagon(vec2 p, float r) {
    p = p.yx;
    const vec3 k = 0.5*vec3(-sqrt(3.0), 1.0, sqrt(4.0/3.0));
    p = abs(p);
    p -= 2.0*min(dot(k.xy,p),0.0)*k.xy;
    p -= vec2(clamp(p.x, -k.z*r, k.z*r), r);
    return length(p)*sign(p.y);
}

float hash(vec2 co) {
    co += 1.234;
    return fract(sin(dot(co.xy ,vec2(12.9898,58.233))) * 13758.5453);
}

float dot2(vec2 p) {
    return dot(p, p);
}

float segment(vec2 p, vec2 a, vec2 b ) {
    vec2 pa = p-a, ba = b-a;
    float h = clamp(dot(pa,ba)/dot(ba,ba), 0.0, 1.0);
    return length(pa - ba*h);
}

float bezier(vec2 pos, vec2 A, vec2 B, vec2 C) {
    vec2 a = B - A;
    vec2 b = A - 2.0*B + C;
    vec2 c = a * 2.0;
    vec2 d = A - pos;
    float kk = 1.0/dot(b,b);
    float kx = kk * dot(a,b);
    float ky = kk * (2.0*dot(a,a)+dot(d,b)) / 3.0;
    float kz = kk * dot(d,a);
    float res = 0.0;
    float p = ky - kx*kx;
    float p3 = p*p*p;
    float q = kx*(2.0*kx*kx-3.0*ky) + kz;
    float h = q*q + 4.0*p3;
    if (h >= 0.0) {
        h = sqrt(h);
        vec2 x = (vec2(h,-h)-q)/2.0;
        vec2 uv = sign(x)*pow(abs(x), vec2(1.0/3.0));
        float t = clamp(uv.x+uv.y-kx, 0.0, 1.0);
        res = dot2(d + (c + b*t)*t);
    } else {
        float z = sqrt(-p);
        float v = acos(q/(p*z*2.0)) / 3.0;
        float m = cos(v);
        float n = sin(v)*1.732050808;
        vec3 t = clamp(vec3(m+m,-n-m,n-m)*z-kx,0.0,1.0);
        res = min(dot2(d+(c+b*t.x)*t.x), dot2(d+(c+b*t.y)*t.y));
    }
    return sqrt(res);
}

vec2 coff(float h) {
    float h0 = h;
    float h1 = fract(h0*9677.0);
    float t = 0.75*mix(0.5, 1.0, h0*h0)*(TIME+1234.5);
    return mix(0.1, 0.2, h1*h1)*sin(t*vec2(1.0, sqrt(0.5)));
}

vec3 aces_approx(vec3 v) {
    v = max(v, 0.0);
    v *= 0.6;
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((v*(a*v+b))/(v*(c*v+d)+e), 0.0, 1.0);
}

vec3 alphaBlend(vec3 back, vec4 front) {
    return mix(back, front.xyz, front.w);
}

vec4 alphaBlend(vec4 back, vec4 front) {
    float w = front.w + back.w*(1.0-front.w);
    vec3 xyz = (front.xyz*front.w + back.xyz*back.w*(1.0-front.w))/w;
    return w > 0.0 ? vec4(xyz, w) : vec4(0.0);
}

vec4 plane(vec3 ro, vec3 rd, vec3 pp, vec3 off, float aa, float n) {
    vec2 p = (pp-off*U.yyx).xy;
    vec2 p2 = p;
    p2 *= ROT(tau*0.1*n+0.05*TIME);
    p2 += 0.125*(ro.z-pp.z)*vec2(1.0)*ROT(tau*hash(vec2(n)));
    vec2 hp = p2;
    hp += 0.5;
    const float z = 1.0/3.0;
    hp /= z;
    vec2 hn = hextile(hp);

    float h0 = hash(hn+n);
    vec2 p0 = coff(h0);

    vec3 bcol = 0.5*(1.0+cos(vec3(0.0, 1.0, 2.0) + 2.0*(p2.x*p2.y+p2.x)-0.33*n));
    vec3 col = vec3(0.0);

    for (int i = 0; i < 6; ++i) {
        float h1 = hash(hn+noff6[i]+n);
        vec2 p1 = off6[i]+coff(h1);

        float h2 = h0+h1;
        float fade = smoothstep(1.05, 0.85, distance(p0, p1));
        if (fade < 0.0125) continue;

#ifdef BEZIER
        vec2 p2b = 0.5*(p1+p0)+coff(h2);
        float dd = bezier(hp, p0, p2b, p1);
#else
        float dd = segment(hp, p0, p1);
#endif
        float gd = abs(dd);
        gd *= sqrt(gd);
        gd = max(gd, 0.0005);

        col += fade*0.002*bcol/(gd);
    }

    {
        float cd = length(hp-p0);
        float gd = abs(cd);
        gd *= gd;
        gd = max(gd, 0.0005);
        col += 0.0025*sqrt(bcol)/(gd);
    }

    {
        float hd = hexagon(hp, 0.485);
        float gd = abs(hd);
        gd = max(gd, 0.005);
        col += 0.0005*bcol*bcol/(gd);
    }

    float l = dot(col, L);
    return vec4(col, tanh_approx(sqrt(l)+dot(p, p)));
}

vec3 color(vec3 ww, vec3 uu, vec3 vv, vec3 ro, vec2 p) {
    vec2 np = p + 1.0/RESOLUTION.xy;
    float rdd = 2.0;

    vec3 rd = normalize(p.x*uu + p.y*vv + rdd*ww);
    vec3 nrd = normalize(np.x*uu + np.y*vv + rdd*ww);

    float nz = floor(ro.z / planeDist);
    vec4 acol = vec4(0.0);
    vec3 skyCol = vec3(0.0);

    for (float i = 1.0; i <= furthest; ++i) {
        float pz = planeDist*nz + planeDist*i;
        float pd = (pz - ro.z)/rd.z;

        if (pd > 0.0 && acol.w < cutOff) {
            vec3 pp = ro + rd*pd;
            vec3 npp = ro + nrd*pd;
            float aa = 3.0*length(pp - npp);
            vec3 off = offset(pp.z);
            vec4 pcol = plane(ro, rd, pp, off, aa, nz+i);
            float nzd = pp.z-ro.z;
            float fadeIn = smoothstep(planeDist*furthest, planeDist*fadeFrom, nzd);
            float fadeOut = smoothstep(0.0, planeDist*0.1, nzd);
            pcol.w *= fadeOut*fadeIn;
            acol = alphaBlend(pcol, acol);
        } else {
            acol.w = acol.w > cutOff ? 1.0 : acol.w;
            break;
        }
    }

    return alphaBlend(skyCol, acol);
}

void mainImage(out vec4 outColor, in vec2 fragCoord) {
    vec2 r = RESOLUTION.xy;
    vec2 q = fragCoord/r.xy;
    vec2 pp = -1.0 + 2.0*q;
    vec2 p = pp;
    p.x *= r.x/r.y;

    float tdist = length(pp);
    float tm = 0.2*planeDist*TIME+0.1*tdist;

    vec3 ro = offset(tm);
    vec3 dro = doffset(tm);
    vec3 ddro = ddoffset(tm);

    vec3 ww = normalize(dro);
    vec3 uu = normalize(cross(U.xyx+ddro, ww));
    vec3 vv = cross(ww, uu);
    vec3 col = color(ww, uu, vv, ro, p);
    col -= 0.02*U.zwx*(length(pp)+0.125);
    col *= smoothstep(1.5, 1.0, length(pp));
    col *= smoothstep(0.0, 10.0, TIME-2.0*(q.x-q.x*q.y));
    col = aces_approx(col);
    col = sqrt(col);
    outColor = vec4(col, 1.0);
}

void main() {
    vec2 fragCoord = texCoord * RESOLUTION.xy;
    mainImage(fragColor, fragCoord);
}
